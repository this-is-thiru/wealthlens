package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A draft's onboarding steps (PRD F34; {@code DR 20} F34; W-122a): which the person has answered and, for each still
 * open, what answering it is measured to be worth, so they can stop at any step knowing what is left (C4a). Computed on
 * every request and stored nowhere; the worths are {@link DraftCompleteness}'s, grouped by step.
 *
 * @param asOf the day it was computed on
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param steps every step, in the order they are asked
 * @param next the first step not answered; empty once all are
 */
public record DraftOnboarding(LocalDate asOf, String regime, int revision, List<Step> steps, Optional<OnboardingStep> next) {

    public DraftOnboarding {
        steps = List.copyOf(steps);
    }

    /**
     * One step.
     *
     * @param step which
     * @param answered whether the person has said there is no more to enter for it
     * @param items for an open step, the completeness items it covers, each with its own worth, largest first; never added,
     *     since they overlap (C3a). None once answered
     * @param notMeasured for an open step nothing measures, why its worth is not stated; empty otherwise
     */
    public record Step(OnboardingStep step, boolean answered, List<DraftCompleteness.Item> items,
            Optional<String> notMeasured) {

        public Step {
            items = List.copyOf(items);
        }
    }
}
