package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A share of a life policy's actual capital sum assured, chosen by when the policy was issued and whom it covers: how
 * much of its premium counts towards s.123 (S2 Schedule XV para 2; D-134, D-135), and the highest premium its sums
 * may bear and still be excluded from total income (S2 Schedule II Sl. 2; 1961 s.10(10D); D-205). A definition carrying its shares, as
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
        return bandHolding(issuedOn, coversDisabilityOrDisease).orElseThrow(() -> new IllegalArgumentException(
                "No premium share band holds a policy issued on " + issuedOn + "; the paragraph gives each policy one"
                        + " share"));
    }

    /**
     * The band for a policy, as {@link #bandFor}, or empty where no band holds it — Schedule II Sl. 2 sets no condition
     * for a policy issued before its first band; more than one is refused.
     */
    public Optional<Band> bandHolding(LocalDate issuedOn, boolean coversDisabilityOrDisease) {
        List<Band> holding = bands.stream()
                .filter(band -> band.window().holds(issuedOn, coversDisabilityOrDisease))
                .toList();
        List<Band> specific = holding.stream().filter(band -> band.window().onlyCoveringDisabilityOrDisease()).toList();
        List<Band> chosen = specific.isEmpty() ? holding : specific;
        if (chosen.size() > 1) {
            throw new IllegalArgumentException(chosen.size() + " premium share bands hold a policy issued on " + issuedOn
                    + "; the paragraph gives each policy one share");
        }
        return chosen.stream().findFirst();
    }
}
