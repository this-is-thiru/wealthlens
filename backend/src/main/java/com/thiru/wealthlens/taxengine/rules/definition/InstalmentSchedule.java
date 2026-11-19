package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.MonthDay;
import java.util.List;

/**
 * When advance tax falls due and how much of it by then (2025 s.408(1) Table, (3); 1961 s.211(1)(a) Table and proviso;
 * W-089c): each instalment's date and the share of the year's advance tax due by it, cumulative.
 *
 * @param instalments in date order through the financial year, the last at the whole of it
 */
public record InstalmentSchedule(List<Instalment> instalments) implements RuleDefinition {

    public InstalmentSchedule {
        if (instalments == null || instalments.isEmpty()) {
            throw new IllegalArgumentException("An instalment schedule needs at least one instalment");
        }
        instalments = List.copyOf(instalments);
        if (instalments.getLast().cumulativeShare().compareTo(Rate.percent("100")) != 0) {
            throw new IllegalArgumentException("The last instalment must be the whole of the advance tax, but was "
                    + instalments.getLast().cumulativeShare());
        }
    }

    /**
     * One instalment.
     *
     * @param dueOn the day of the year it falls due on or before
     * @param cumulativeShare the share of the year's advance tax due by then, counting earlier instalments
     */
    public record Instalment(MonthDay dueOn, Rate cumulativeShare) {

        public Instalment {
            if (dueOn == null || cumulativeShare == null || cumulativeShare.compareTo(Rate.ZERO) <= 0) {
                throw new IllegalArgumentException("An instalment needs a date and a share above nil, but was " + dueOn
                        + " and " + cumulativeShare);
            }
        }
    }
}
