package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ClaimPlaybook;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every claim of a draft's year, as the claim catalogue states how it is made ({@code DR 22}; PRD F40; W-118): those the
 * draft's computation allows, each with what it takes off income and off the bill, and the year's others. Computed on
 * every request and stored nowhere.
 *
 * @param asOf the day it was computed on
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param claimed the claims the computation allows, by rule id
 * @param notClaimed the year's other claims, without a measure
 * @param notIncluded what these claims do not yet say
 */
public record DraftClaims(LocalDate asOf, String regime, int revision, List<Claim> claimed, List<Claim> notClaimed,
        List<String> notIncluded) {

    public DraftClaims {
        claimed = List.copyOf(claimed);
        notClaimed = List.copyOf(notClaimed);
        notIncluded = List.copyOf(notIncluded);
    }

    /**
     * One claim.
     *
     * @param playbook how it is made
     * @param stepDates the day each dated step falls on for this draft — the tax year's end and the return's due date;
     *     a step by the employer's cut-off, or by no deadline, has none (W-118 C5a)
     * @param allowed what it takes off total income, where the computation allows it
     * @param worth what it takes off the bill, where the computation allows it, measured by a second run
     */
    public record Claim(ClaimPlaybook playbook, List<StepDate> stepDates, Optional<Money> allowed,
            Optional<Money> worth) {

        public Claim {
            stepDates = List.copyOf(stepDates);
        }
    }

    /** The day a step falls on. */
    public record StepDate(int order, LocalDate on) {
    }
}
