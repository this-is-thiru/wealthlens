package com.thiru.wealthlens.taxplanning.portfolio;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsAmounts;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.SideCharges;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.ChargeSide;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.ArrayList;
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
 * <p>It carries the amounts the gain is computed from — the consideration, the cost and every charge on each side by
 * its catalogue code, the user-entered misc charge as {@code MISC} — and no gain: which charges reduce it is the rule
 * data's ({@code RS-CG-CHARGE}; 2025 s.72; 1961 s.48; W-162a, D-375).
 *
 * <p>Nothing is defaulted. Brought-forward losses are the tax ledger's (S2 s.111), not {@code portfolio}'s, so none arrive here.
 */
@Component
@RequiredArgsConstructor
public class CapitalGainsAdapter {

    /** The code a user-entered charge reaches the engine under; the charge catalogue holds no such code. */
    static final String MISC = "MISC";

    private final CapitalGainsSummaryService capitalGainsSummaryService;

    /** The capital gains of {@code email} for {@code year}, pinned to the summary they were read from. */
    public CapitalGainsInput inputFor(String email, TaxYearId year) {
        CapitalGainsSummary summary =
                capitalGainsSummaryService.summaryFor(email, new FinancialYear(year.startYear()).label());
        List<CapitalGainsInput.Bucket> buckets = summary.buckets().stream()
                .map(CapitalGainsAdapter::bucketOf)
                .flatMap(Optional::stream)
                .toList();
        return new CapitalGainsInput(buckets, summary.asOf(), summary.portfolioComputationRef());
    }

    /** The engine's bucket, or none where every amount is nil: nothing was fetched, paid or charged. */
    private static Optional<CapitalGainsInput.Bucket> bucketOf(CapitalGainsBucket bucket) {
        return isNil(bucket.amounts()) ? Optional.empty() : Optional.of(engineBucket(bucket));
    }

    private static boolean isNil(CapitalGainsAmounts amounts) {
        return amounts.considerationRupees() == 0 && amounts.costRupees() == 0 && isNil(amounts.acquisition())
                && isNil(amounts.transfer());
    }

    private static boolean isNil(SideCharges side) {
        return side.miscRupees() == 0 && side.rupeesByCode().values().stream().allMatch(rupees -> rupees == 0);
    }

    /** The bucket as the engine reads it; portfolio's own term is not passed — the engine decides it (D-305). */
    private static CapitalGainsInput.Bucket engineBucket(CapitalGainsBucket bucket) {
        BucketFacts facts = new BucketFacts(bucket.assetType().name(), bucket.sttOnAcquisition().name(),
                bucket.sttOnTransfer().name(), bucket.acquisition().mode().name(),
                bucket.acquisition().corporateAction().map(Enum::name), bucket.acquiredOn(), bucket.transferredOn(),
                sideVenueOf(bucket.acquisitionVenue()), sideVenueOf(bucket.transferVenue()));
        CapitalGainsAmounts amounts = bucket.amounts();
        List<Charge> charges = new ArrayList<>(chargesOf(ChargeSide.ACQUISITION, amounts.acquisition()));
        charges.addAll(chargesOf(ChargeSide.TRANSFER, amounts.transfer()));
        return new CapitalGainsInput.Bucket(MoneyBridge.fromTMoney(amounts.considerationRupees()),
                MoneyBridge.fromTMoney(amounts.costRupees()), charges, facts, Optional.empty());
    }

    /** Every code the side recorded, a nil one too, since a code is a fact; the misc charge where there is one. */
    private static List<Charge> chargesOf(ChargeSide side, SideCharges charges) {
        List<Charge> out = new ArrayList<>();
        charges.rupeesByCode().forEach((code, rupees) -> out.add(new Charge(side, code, MoneyBridge.fromTMoney(rupees))));
        if (charges.miscRupees() != 0) {
            out.add(new Charge(side, MISC, MoneyBridge.fromTMoney(charges.miscRupees())));
        }
        return out;
    }

    private static SideVenue sideVenueOf(TradeVenue venue) {
        return new SideVenue(venue.exchange(), venue.segment().map(Enum::name));
    }
}
