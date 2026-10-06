package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.util.List;

/**
 * How much of a life policy's premium counts towards s.123: a share of its actual capital sum assured, chosen by when
 * the policy was issued and whom it covers (S2 Schedule XV para 2; D-134, D-135). A definition carrying its shares, as
 * {@link DisabledTransportLimit} carries its rupees, because a rule version is a cap or a definition, never both.
 *
 * @param bands each window with its share, as the paragraph lists them
 */
public record PremiumShareBands(List<Band> bands) implements RuleDefinition {

    /**
     * One band of the paragraph.
     *
     * @param window the issue dates, and whom the policy must cover, the band holds
     * @param share the share of the actual capital sum assured
     */
    public record Band(PolicyIssueWindow window, Rate share) {

        public Band {
            if (window == null || share == null) {
                throw new IllegalArgumentException("A premium share band needs its window and its share");
            }
        }
    }

    public PremiumShareBands {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("Premium share bands need at least one band");
        }
        bands = List.copyOf(bands);
    }

    /**
     * The band for a policy: of those holding it, the one only for a policy covering a person with a disability or
     * specified disease where the policy covers one — it is the more specific; exactly one must remain.
     */
    public Band bandFor(LocalDate issuedOn, boolean coversDisabilityOrDisease) {
        List<Band> holding = bands.stream()
                .filter(band -> band.window().holds(issuedOn, coversDisabilityOrDisease))
                .toList();
        List<Band> specific = holding.stream().filter(band -> band.window().onlyCoveringDisabilityOrDisease()).toList();
        List<Band> chosen = specific.isEmpty() ? holding : specific;
        if (chosen.size() != 1) {
            throw new IllegalArgumentException(chosen.size() + " premium share bands hold a policy issued on " + issuedOn
                    + "; the paragraph gives each policy one share");
        }
        return chosen.getFirst();
    }
}
