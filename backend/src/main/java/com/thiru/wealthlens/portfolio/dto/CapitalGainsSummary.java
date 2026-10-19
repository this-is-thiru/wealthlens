package com.thiru.wealthlens.portfolio.dto;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A user's realised capital gains for one financial year, the read-model {@code portfolio} publishes for the tax side
 * ({@code 07-architecture.md} X1.3; {@code 15-review-findings.md} F5; D-237).
 *
 * <p>A published read-model rather than the entities, because {@code RealisedProfits} is mid-cutover and sums every
 * asset per classification, which hides the rate basis. It carries no brought-forward loss: what an earlier year's
 * loss left after that return's own set-off is a tax figure, kept on the tax side with S2 s.111.
 *
 * @param email whose gains these are
 * @param financialYear the year as {@code portfolio} keys it, such as {@code 2025-2026} ({@code TLocalDate.financialYear})
 * @param buckets one per classification, asset type, sub-class, STT on each side, acquisition and its date; may be
 *     empty
 * @param asOf when {@code portfolio} computed this, so a computation can pin what it ran against
 * @param portfolioComputationRef {@code portfolio}'s reference for the run that produced it
 */
public record CapitalGainsSummary(
        String email, String financialYear, List<CapitalGainsBucket> buckets, Instant asOf,
        String portfolioComputationRef) {

    public CapitalGainsSummary {
        if (email == null || email.isBlank() || financialYear == null || buckets == null || asOf == null
                || portfolioComputationRef == null || portfolioComputationRef.isBlank()) {
            throw new IllegalArgumentException("A capital-gains summary needs the user, the financial year, the"
                    + " buckets, when it was computed and the run that computed it");
        }
        buckets = List.copyOf(buckets);
        requireOneBucketPerKey(buckets);
    }

    /** Two buckets of one key would be one amount split, which a reader summing by key would get right only by luck. */
    private static void requireOneBucketPerKey(List<CapitalGainsBucket> buckets) {
        Set<List<Object>> keys = new HashSet<>();
        for (CapitalGainsBucket bucket : buckets) {
            if (!keys.add(List.of(bucket.classification(), bucket.assetType(), bucket.instrumentSubClass(),
                    bucket.sttOnAcquisition(), bucket.sttOnTransfer(), bucket.acquisition(),
                    bucket.acquiredOn()))) {
                throw new IllegalArgumentException("Two capital-gains buckets are " + bucket.classification() + " "
                        + bucket.assetType() + " " + bucket.instrumentSubClass().orElse("without a sub-class")
                        + " with the same STT and acquisition; publish one per classification, asset type, sub-class,"
                        + " STT and acquisition");
            }
        }
    }
}
