package com.thiru.wealthlens.taxengine.input;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The end of an employment a gratuity is measured on (S2 s.19(1) Table Sl. 6; 1961 s.10(10)(iii); D-106): when the
 * service began, the day of the event — retirement, incapacity, death or termination — and what each of the ten
 * months "immediately preceding the month when any such event occurs" paid.
 *
 * <p>The months are entered rather than taken from the year's pay lines because they straddle tax years: the ten
 * months before a June retirement fall mostly in the previous one.
 *
 * @param serviceBegan the first day of service with the employer
 * @param separatedOn the day of the event
 * @param monthsBefore the ten calendar months before the event's month, each with what it paid
 */
public record Separation(LocalDate serviceBegan, LocalDate separatedOn, List<MonthOfPay> monthsBefore) {

    private static final int MONTHS = 10;

    public Separation {
        if (serviceBegan == null || separatedOn == null || monthsBefore == null) {
            throw new IllegalArgumentException("A separation needs when service began, the event's day and the ten"
                    + " months before it");
        }
        if (!serviceBegan.isBefore(separatedOn)) {
            throw new IllegalArgumentException("Service began on " + serviceBegan + ", not before the event on "
                    + separatedOn);
        }
        List<YearMonth> expected = IntStream.rangeClosed(1, MONTHS)
                .mapToObj(back -> YearMonth.from(separatedOn).minusMonths(MONTHS + 1L - back))
                .toList();
        List<YearMonth> entered = monthsBefore.stream().map(MonthOfPay::month).sorted().toList();
        if (!entered.equals(expected)) {
            throw new IllegalArgumentException("The ten months immediately preceding the month of the event on "
                    + separatedOn + " are " + expected.getFirst() + " to " + expected.getLast() + ", but "
                    + entered + " were entered");
        }
        monthsBefore = List.copyOf(monthsBefore);
    }

    /** The completed years of service: "each completed year of service" — a part year does not count. */
    public int completedYears() {
        return Period.between(serviceBegan, separatedOn).getYears();
    }
}
