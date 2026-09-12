package com.thiru.wealthlens.shared.util.time;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;

public class TLocalDate {

    public static String TIME_ZONE_IST = "Asia/Kolkata";

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static Month firstMonthOfQuarter(int quarter) {
        if (quarter <= 0 || quarter >= 5) {
            throw new IllegalArgumentException("Valid quarters are 1(Q1) to 4(Q4)");
        }
        return Month.of(((quarter - 1) * 3) + 1);
    }


    public static String lastYearSameDateInString() {
        return LocalDate.now().minusYears(1).toString();
    }

    public static String convertToString(LocalDate date) {
        return date.toString();
    }

    public static LocalDate convertToDate(String date) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TCollectionUtil.DATE_FORMAT);
        return LocalDate.parse(date, formatter);
    }

    public static LocalDateTime convertToDateTime(String date) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TCollectionUtil.DATE_TIME_FORMAT);
        return LocalDateTime.parse(date, formatter);
    }

    public static String standardDateFormattedString(LocalDate date) {

        LocalDate localDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TCollectionUtil.DATE_FORMAT);
        return localDate.format(formatter);
    }

    /**
     * The Indian financial year a date falls in, as {@code "2023-2024"}.
     *
     * <p>Runs 1 April to <b>31 March inclusive</b>. Three separate copies of this derivation
     * existed — in {@code ProfitAndLossService}, in the V1 sell, and in the trade-outcome recorder —
     * and all three compared {@code isBefore(March 31)}, which puts a trade made <em>on</em> 31
     * March into the following year. One day wrong, every year, and 31 March is a heavy day for
     * tax-loss harvesting. They now all call this.
     */
    public static String financialYear(LocalDate date) {
        int year = date.getYear();
        return date.getMonthValue() <= Month.MARCH.getValue()
                ? (year - 1) + "-" + year
                : year + "-" + (year + 1);
    }
}
