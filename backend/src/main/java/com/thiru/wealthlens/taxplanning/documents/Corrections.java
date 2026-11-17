package com.thiru.wealthlens.taxplanning.documents;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Reads a row a person wrote anew, by the kind of value the row holds (W-131d1, D-765 C2a): rupees, {@code yes}/{@code no}, a
 * period as {@code from/to}, or words — the forms {@link ParseView.Field#value()} shows. Text that does not read names its row.
 */
final class Corrections {

    private Corrections() {
    }

    /** The kind a value read has, as the layout names kinds. */
    static String kindOf(ExtractedValue value) {
        return switch (value) {
            case ExtractedValue.MoneyRead money -> "AMOUNT";
            case ExtractedValue.FlagRead flag -> "YES_NO";
            case ExtractedValue.PeriodRead period -> "PERIOD";
            case ExtractedValue.TextRead words -> "TEXT";
        };
    }

    static ExtractedValue read(String path, String kind, String text) {
        String written = text == null ? "" : text.trim();
        try {
            return switch (kind) {
                case "AMOUNT" -> new ExtractedValue.MoneyRead(Money.of(written));
                case "YES_NO" -> flag(path, written);
                case "PERIOD" -> period(path, written);
                case "TEXT" -> new ExtractedValue.TextRead(written);
                default -> throw new BadRequestException(path + " holds no value that can be written");
            };
        } catch (NumberFormatException e) {
            throw new BadRequestException(path + ": '" + written + "' is not an amount in rupees, e.g. 950000 or 950000.50");
        } catch (DateTimeParseException e) {
            throw new BadRequestException(path + ": '" + written + "' is not a period as yyyy-MM-dd/yyyy-MM-dd");
        }
    }

    private static ExtractedValue flag(String path, String written) {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "yes" -> new ExtractedValue.FlagRead(true);
            case "no" -> new ExtractedValue.FlagRead(false);
            default -> throw new BadRequestException(path + ": '" + written + "' is not yes or no");
        };
    }

    private static ExtractedValue period(String path, String written) {
        String[] days = written.split("/");
        if (days.length != 2) {
            throw new BadRequestException(path + ": '" + written + "' is not a period as yyyy-MM-dd/yyyy-MM-dd");
        }
        return new ExtractedValue.PeriodRead(LocalDate.parse(days[0].trim()), LocalDate.parse(days[1].trim()));
    }
}
