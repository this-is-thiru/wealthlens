package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.component.Liquidity;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * W-238e1 — every fund and every employee's contribution accounts for each day of the year it lives in: a withdrawal
 * term, or a period the pack says it cannot state, and why. Money paid into a fund is what the optimiser suggests in
 * place of pay in hand, so a row that is silent about when it comes out would let a suggestion read as if it never
 * locked anything (A2.6).
 */
final class LiquidityCoverage {

    private LiquidityCoverage() {
    }

    static void check(RulePack pack, List<String> problems) {
        DateRange year = DateRange.between(pack.taxYear().startsOn(), pack.taxYear().endsOn());
        for (EmployerFundDefinition fund : pack.funds().values()) {
            require("employer fund " + fund.code(), fund.liquidity(), within(year, fund.validity()), problems);
        }
        for (ComponentDefinition component : pack.components().values()) {
            if (component.kind() == ComponentKind.EMPLOYEE_CONTRIBUTION) {
                require("component " + component.code(), component.liquidity(),
                        within(year, component.lifecycle().validity()), problems);
            }
        }
    }

    private static void require(String row, Liquidity liquidity, Optional<DateRange> span, List<String> problems) {
        span.flatMap(liquidity::firstUnaccountedDay).ifPresent(day -> problems.add(row + " states no withdrawal term"
                + " and no unstated period for " + day + "; each day it lives in the year needs one"));
    }

    /** The days of {@code year} that {@code row} also covers, if any. */
    private static Optional<DateRange> within(DateRange year, DateRange row) {
        if (!year.overlaps(row)) {
            return Optional.empty();
        }
        LocalDate from = row.from().isAfter(year.from()) ? row.from() : year.from();
        LocalDate to = row.to().filter(end -> end.isBefore(year.to().orElseThrow())).orElse(year.to().orElseThrow());
        return Optional.of(DateRange.between(from, to));
    }
}
