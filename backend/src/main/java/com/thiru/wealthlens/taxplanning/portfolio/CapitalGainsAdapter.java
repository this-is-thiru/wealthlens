package com.thiru.wealthlens.taxplanning.portfolio;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.RateBasisHint;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns {@code portfolio}'s capital-gains read-model into the engine's {@link CapitalGainsInput} ({@code 13-edge-cases.md}
 * R-10; {@code 07-architecture.md} X1.3; D-241).
 *
 * <p>The tax side decides here what {@code portfolio} could not: each bucket's term and rate basis, from the
 * conditions of S2 ss.196–198 and 1961 ss.111A, 112, 112A, which are the same in both years
 * ({@code sources/2026-10-18-capital-gains-basis.md}). A short-term gain on equity whose sale bore STT, and a long-term
 * one whose buy and sale both did, fall on the listed-equity rates; without STT on the sale the short-term gain is
 * charged with the rest of total income, the long-term one under s.197 / s.112. A long-term gain on equity bought
 * without STT turns on a notification not yet read (OQ-31), and is refused.
 *
 * <p>Nothing is defaulted. An unclassified gain blocks, as does speculative business income, which this engine does
 * not compute; any other asset with a capital-gains classification blocks until the instrument registry classifies
 * it. Fixed-deposit interest ({@code NOT_CAPITAL_GAINS}) is other-sources income, entered there, and is left out.
 * Brought-forward losses are the tax ledger's (S2 s.111), not {@code portfolio}'s, so none arrive here.
 */
@Component
@RequiredArgsConstructor
public class CapitalGainsAdapter {

    private final CapitalGainsSummaryService capitalGainsSummaryService;

    /**
     * The capital gains of {@code email} for {@code year}, pinned to the summary they were read from.
     *
     * @throws IllegalArgumentException if a gain is unclassified, speculative, of an asset not yet mapped, or turns on
     *     OQ-31
     */
    public CapitalGainsInput inputFor(String email, TaxYearId year) {
        CapitalGainsSummary summary =
                capitalGainsSummaryService.summaryFor(email, TLocalDate.financialYear(year.startsOn()));
        List<CapitalGainsInput.Bucket> buckets = summary.buckets().stream()
                .map(CapitalGainsAdapter::bucketOf)
                .flatMap(Optional::stream)
                .toList();
        return new CapitalGainsInput(buckets, summary.asOf(), summary.portfolioComputationRef());
    }

    /** The engine's bucket, or none for a nil amount and for income of another head. */
    private static Optional<CapitalGainsInput.Bucket> bucketOf(CapitalGainsBucket bucket) {
        if (bucket.gainRupees() == 0) {
            return Optional.empty();
        }
        return switch (bucket.classification()) {
            case SHORT_TERM -> Optional.of(engineBucket(bucket, Term.SHORT_TERM, shortTermBasis(bucket)));
            case LONG_TERM -> Optional.of(engineBucket(bucket, Term.LONG_TERM, longTermBasis(bucket)));
            case NOT_CAPITAL_GAINS -> Optional.empty();
            case UNCLASSIFIED -> throw new IllegalArgumentException("Realised gains of " + bucket.gainRupees() + " on "
                    + bucket.assetType() + " are unclassified — a fund or bond awaiting the instrument registry, or a"
                    + " derivative — so they cannot be taxed; classify them before computing (X1.3)");
            case SPECULATIVE -> throw new IllegalArgumentException("Intraday gains of " + bucket.gainRupees()
                    + " are speculative business income, which this engine does not compute (ITR-3)");
        };
    }

    private static CapitalGainsInput.Bucket engineBucket(CapitalGainsBucket bucket, Term term, RateBasisHint basis) {
        return new CapitalGainsInput.Bucket(MoneyBridge.fromTMoney(bucket.gainRupees()), term, basis,
                Optional.empty());
    }

    /** S2 s.196(1); 1961 s.111A(1): equity sold with STT. Any other short-term gain is charged with total income. */
    private static RateBasisHint shortTermBasis(CapitalGainsBucket bucket) {
        requireMapped(bucket);
        return bucket.sttOnTransfer() ? RateBasisHint.SPECIAL_RATE_LISTED_EQUITY : RateBasisHint.SLAB;
    }

    /** S2 s.198(1); 1961 s.112A(1): equity bought and sold with STT. Without STT on the sale, s.197 / s.112. */
    private static RateBasisHint longTermBasis(CapitalGainsBucket bucket) {
        requireMapped(bucket);
        if (!bucket.sttOnTransfer()) {
            return RateBasisHint.SPECIAL_RATE_OTHER;
        }
        if (!bucket.sttOnAcquisition()) {
            throw new IllegalArgumentException("Long-term gains of " + bucket.gainRupees() + " on equity bought without"
                    + " securities transaction tax fall under s.112A / s.198 only for the acquisitions a notification"
                    + " names, which is not yet read (OQ-31)");
        }
        return RateBasisHint.SPECIAL_RATE_LISTED_EQUITY;
    }

    /** Only listed equity reaches a capital-gains classification today; anything else needs its own rule first. */
    private static void requireMapped(CapitalGainsBucket bucket) {
        if (bucket.assetType() != AssetType.EQUITY) {
            throw new IllegalArgumentException("Capital gains on " + bucket.assetType() + " have no rate-basis rule yet;"
                    + " only equity is mapped (D-241)");
        }
    }
}
