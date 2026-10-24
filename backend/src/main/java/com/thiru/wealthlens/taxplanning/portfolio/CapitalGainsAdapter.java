package com.thiru.wealthlens.taxplanning.portfolio;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns {@code portfolio}'s capital-gains read-model into the engine's {@link CapitalGainsInput} ({@code 13-edge-cases.md}
 * R-10; {@code 07-architecture.md} X1.3; D-241).
 *
 * <p>It carries the facts the term and the special-rate provisions turn on — the asset, STT on the buy and the sale,
 * where each side was traded, how and when the lot was acquired and when it was sold — as {@code portfolio} recorded
 * them. Whether a bucket is short-term or long-term (1961 s.2(42A); 2025 s.2(101); D-305) and which part of the head it
 * falls in (S2 ss.196–198; 1961 ss.111A, 112, 112A; S.O. 5054(E); D-289) are the rule data's, not this class's;
 * {@code portfolio}'s own classification is not read: whether the head takes a bucket at all — not a speculative
 * transaction, a derivative or a deposit's interest — is the engine's rows' too (W-160a, D-363).
 *
 * <p>Nothing is defaulted. Brought-forward losses are the tax ledger's (S2 s.111), not {@code portfolio}'s, so none arrive here.
 */
@Component
@RequiredArgsConstructor
public class CapitalGainsAdapter {

    private final CapitalGainsSummaryService capitalGainsSummaryService;

    /** The capital gains of {@code email} for {@code year}, pinned to the summary they were read from. */
    public CapitalGainsInput inputFor(String email, TaxYearId year) {
        CapitalGainsSummary summary =
                capitalGainsSummaryService.summaryFor(email, TLocalDate.financialYear(year.startsOn()));
        List<CapitalGainsInput.Bucket> buckets = summary.buckets().stream()
                .map(CapitalGainsAdapter::bucketOf)
                .flatMap(Optional::stream)
                .toList();
        return new CapitalGainsInput(buckets, summary.asOf(), summary.portfolioComputationRef());
    }

    /** The engine's bucket, or none for a nil amount. */
    private static Optional<CapitalGainsInput.Bucket> bucketOf(CapitalGainsBucket bucket) {
        return bucket.gainRupees() == 0 ? Optional.empty() : Optional.of(engineBucket(bucket));
    }

    /** The bucket as the engine reads it; portfolio's own term is not passed — the engine decides it (D-305). */
    private static CapitalGainsInput.Bucket engineBucket(CapitalGainsBucket bucket) {
        BucketFacts facts = new BucketFacts(bucket.assetType().name(), bucket.sttOnAcquisition().name(),
                bucket.sttOnTransfer().name(), bucket.acquisition().mode().name(),
                bucket.acquisition().corporateAction().map(Enum::name), bucket.acquiredOn(), bucket.transferredOn(),
                sideVenueOf(bucket.acquisitionVenue()), sideVenueOf(bucket.transferVenue()));
        return new CapitalGainsInput.Bucket(MoneyBridge.fromTMoney(bucket.gainRupees()), facts, Optional.empty());
    }

    private static SideVenue sideVenueOf(TradeVenue venue) {
        return new SideVenue(venue.exchange(), venue.segment().map(Enum::name));
    }
}
