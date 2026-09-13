package com.thiru.wealthlens.shared.util.parser;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One column of an export: the name a caller selects it by, its heading, and where its value comes
 * from.
 *
 * <p><b>Why the three travel together.</b> This application had two export mechanisms and both
 * separated them. One kept headings in a {@code String[]} and values in {@code createCell(0)}…
 * {@code createCell(12)}; the other kept a declared order, a header map and an extractor map, and
 * built its header row from a different list than its data. Both produced spreadsheets whose
 * columns did not describe the values beneath them, and neither failed a test, because a heading
 * with the wrong value under it is still a valid file. Binding the three into one record is what
 * makes that unrepresentable rather than merely fixed.
 *
 * <p><b>Still declared, not derived.</b> Reflecting over a DTO's fields would also remove the
 * duplication, and would be worse: it makes the file format an accident of field declaration order,
 * so adding a field silently moves a column in a spreadsheet someone has built formulas against. An
 * export is a published contract. Every column is written down on purpose; the change is that
 * writing one down is a single line in a single place.
 *
 * @param field  the stable name a caller names in {@code selectedColumns}. Part of the API — it
 *               outlives any renaming of the heading beside it
 * @param header the heading a human reads
 * @param value  what to put in the cell
 */
public record ExcelColumn<T>(String field, String header, Function<T, Object> value) {

    /** A plain string column. Enums should use this with {@code Enum::name}. */
    public static <T> ExcelColumn<T> text(String field, String header, Function<T, String> value) {
        return new ExcelColumn<>(field, header, value::apply);
    }

    /**
     * A rupee amount, rounded the way the rest of the application rounds.
     *
     * <p>Null becomes zero: a blank cell where a figure belongs reads as missing data rather than
     * as nothing charged, and several of these come from boxed fields.
     */
    public static <T> ExcelColumn<T> money(String field, String header, Function<T, Double> value) {
        return new ExcelColumn<>(field, header, row -> {
            Double amount = value.apply(row);
            return TMoney.scale(amount == null ? 0.0 : amount);
        });
    }

    /** A quantity. Rounded like money because it is displayed to the same precision. */
    public static <T> ExcelColumn<T> quantity(String field, String header, Function<T, Double> value) {
        return money(field, header, value);
    }

    /** A date, formatted by the application's own date format. */
    public static <T> ExcelColumn<T> date(String field, String header, Function<T, LocalDate> value) {
        return new ExcelColumn<>(field, header, value::apply);
    }

    /**
     * The caller's chosen columns, in the order they asked for them, or all of them when they asked
     * for nothing.
     *
     * <p>An unrecognised name is refused rather than dropped. Dropping it returns a spreadsheet
     * quietly missing a column that was asked for, which is the same silent-wrong this type exists
     * to prevent; and the previous implementation did neither, reaching a null index and failing as
     * a 500 for what is the caller's typo.
     *
     * @throws BadRequestException if a requested field is not one this export declares
     */
    public static <T> List<ExcelColumn<T>> select(List<ExcelColumn<T>> declared, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return declared;
        }

        Map<String, ExcelColumn<T>> byField = new LinkedHashMap<>();
        declared.forEach(column -> byField.put(column.field(), column));

        List<String> unknown = fields.stream().filter(field -> !byField.containsKey(field)).toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Cannot export " + unknown + ": not a column of this report."
                    + " Available columns are " + byField.keySet());
        }
        return fields.stream().map(byField::get).toList();
    }
}
