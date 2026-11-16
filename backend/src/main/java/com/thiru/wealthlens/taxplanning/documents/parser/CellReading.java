package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a line's value text as its kind says (L2, {@code DR 06} G5.1; W-127a, D-732 C2a): an amount is numbers only,
 * grouped the Indian way ({@code 12,00,000.00}) or the Western ({@code 1,200,000.00}) or not at all, with an optional
 * leading minus and at most two decimals, one per printed column; Yes or No; words as they are. Anything else is not read
 * — the text is kept, and nothing read is never zero ({@code D-NOSILENT}).
 */
final class CellReading {

    private static final Pattern AMOUNT = Pattern.compile(
            "^-?(\\d{1,2}(,\\d{2})*,\\d{3}|\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d{1,2})?$");

    private CellReading() {
    }

    /** One amount per column, in printed order; empty when the text is not exactly that many amounts. */
    static Optional<List<ExtractedValue>> amounts(String text, int columns) {
        String[] printed = text.trim().split("\\s+");
        if (text.isBlank() || printed.length != columns) {
            return Optional.empty();
        }
        List<ExtractedValue> values = new ArrayList<>();
        for (String amount : printed) {
            if (!AMOUNT.matcher(amount).matches()) {
                return Optional.empty();
            }
            values.add(new ExtractedValue.MoneyRead(Money.of(amount.replace(",", ""))));
        }
        return Optional.of(values);
    }

    /** Yes or No, in any case; empty for anything else. */
    static Optional<ExtractedValue> yesNo(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "yes" -> Optional.of(new ExtractedValue.FlagRead(true));
            case "no" -> Optional.of(new ExtractedValue.FlagRead(false));
            default -> Optional.empty();
        };
    }
}
