package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import java.util.List;
import java.util.Optional;

/**
 * Every regime priced on a draft, and whether those figures are coarse (PRD F34.3; W-122b1 C2a, D-723): the engine's
 * comparison as it gives it, with the label the draft's onboarding steps decide. No band per regime.
 *
 * @param chosen the regime the year's election resolves to
 * @param mode how it was chosen, as the engine names it
 * @param priced each regime with its bill
 * @param coarse whether any onboarding step is still unanswered
 * @param says while coarse, the year's sentence saying so
 */
public record DraftRegimeComparison(String chosen, String mode, List<RegimeComparison.Priced> priced, boolean coarse,
        Optional<String> says) {

    public DraftRegimeComparison {
        priced = List.copyOf(priced);
    }
}
