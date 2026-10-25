package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.TradeVenue;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.util.money.TMoney;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a user's realised capital gains for a financial year as {@link CapitalGainsSummary} — the one read-model
 * the tax side consumes, called synchronously ({@code 07-architecture.md} X1.3, X1.4; D-238).
 *
 * <p>Built from {@code trade_outcomes}, the only record that keeps the asset and its sub-class beside each disposal.
 * A row's gain is its sale value less its adjusted cost less the charges the catalogue marks deductible for capital
 * gains — not {@code netProfit}, which deducts every charge, STT included. User-entered misc charges carry no code,
 * so nothing says they are deductible, and they are not deducted. A row that bore broker charges but kept no
 * breakdown of them cannot be quantified at all, and refuses the year rather than overstating its gain. A row with no
 * breakdown on a side says nothing of securities transaction tax there, so that side's STT is {@code UNKNOWN}, never
 * {@code NOT_PAID} (D-292).
 *
 * <p>Rows are chosen by sell date, not by their stored year, which is written in two forms (W-086a).
 */
@Service
@Log4j2
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CapitalGainsSummaryService {

    /** The charge catalogue's code for securities transaction tax. */
    private static final String STT = "STT";

    private final TradeOutcomeRepository tradeOutcomeRepository;

    /**
     * The summary of {@code email}'s disposals sold in {@code financialYear}.
     *
     * @param financialYear the year as {@link FinancialYear#label()} writes it, such as {@code 2025-2026}
     * @throws IllegalArgumentException if the year is malformed, or a disposal's deductible charges are unknown
     */
    public CapitalGainsSummary summaryFor(String email, String financialYear) {
        FinancialYear year = FinancialYear.parse(financialYear);
        // The finder excludes both ends, so the day before the year opens and the day after it closes bound it.
        List<TradeOutcomeEntity> rows = tradeOutcomeRepository.findByEmailAndSellDateBetween(email,
                year.startsOn().minusDays(1), year.endsOn().plusDays(1));
        requireQuantifiable(rows);
        Map<BucketKey, Double> gainByKey = new LinkedHashMap<>();
        // Nulls sort first so a row without a sell date reaches BucketKey's refusal, which names it.
        rows.stream()
                .sorted(Comparator.comparing(TradeOutcomeEntity::getSellDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(TradeOutcomeEntity::getId))
                .forEach(row -> gainByKey.merge(BucketKey.of(row), gainOf(row), TMoney::add));
        List<CapitalGainsBucket> buckets = gainByKey.entrySet().stream()
                .map(entry -> entry.getKey().bucket(entry.getValue()))
                .toList();
        log.debug("Capital-gains summary for {} {}: {} disposals in {} buckets", email, financialYear, rows.size(),
                buckets.size());
        return new CapitalGainsSummary(email, financialYear, buckets, Instant.now(), referenceOf(rows));
    }

    /** Every row's deductible charges are known: a charge with no breakdown cannot be split into deductible parts. */
    private static void requireQuantifiable(List<TradeOutcomeEntity> rows) {
        List<TradeOutcomeEntity> unknown = rows.stream()
                .filter(row -> withoutBreakup(row.getBuyBrokerCharges(), row.getBuyChargeBreakup())
                        || withoutBreakup(row.getSellBrokerCharges(), row.getSellChargeBreakup()))
                .toList();
        if (!unknown.isEmpty()) {
            TradeOutcomeEntity first = unknown.getFirst();
            throw new IllegalArgumentException(unknown.size() + " disposal(s) bore broker charges but kept no breakdown"
                    + " of them, so the part that reduces a capital gain is unknown — the first sold "
                    + first.getStockCode() + " on " + first.getSellDate() + "; record those trades again");
        }
    }

    private static boolean withoutBreakup(double charges, Map<String, Double> breakup) {
        return charges != 0 && (breakup == null || breakup.isEmpty());
    }

    /** The sale value less the adjusted cost less the deductible charges, to the paisa. */
    private static double gainOf(TradeOutcomeEntity row) {
        if (row.getSellQuantity() == null || row.getBuyQuantity() == null) {
            throw new IllegalArgumentException("The disposal " + row.getId() + " has no quantity, so its gain cannot"
                    + " be computed");
        }
        double sale = row.getSellPrice() * row.getSellQuantity();
        double cost = row.getCorporateActionAdjustedBuyPrice() * row.getBuyQuantity();
        return TMoney.scale(sale - cost - row.getDeductibleBuyCharges() - row.getDeductibleSellCharges());
    }

    /** What a bucket is one of: the rows a bucket sums share all nine. */
    private record BucketKey(AssetType assetType, Optional<String> subClass,
            SttEvidence sttOnAcquisition, SttEvidence sttOnTransfer, TradeVenue acquisitionVenue,
            TradeVenue transferVenue, Acquisition acquisition, LocalDate acquiredOn,
            LocalDate transferredOn) {

        static BucketKey of(TradeOutcomeEntity row) {
            return new BucketKey(row.getAssetType(),
                    Optional.ofNullable(row.getInstrumentSubClass()), sttEvidenceOf(row.getBuyChargeBreakup()),
                    sttEvidenceOf(row.getSellChargeBreakup()),
                    TradeVenue.stored(row.getBuyExchangeName(), row.getBuySegment()),
                    TradeVenue.stored(row.getSellExchangeName(), row.getSellSegment()),
                    Acquisition.stored(row.getAcquisitionMode(), row.getAcquisitionCorporateAction()),
                    acquiredOnOf(row), transferredOnOf(row));
        }

        CapitalGainsBucket bucket(double gainRupees) {
            return new CapitalGainsBucket(assetType, subClass, sttOnAcquisition, sttOnTransfer,
                    acquisitionVenue, transferVenue, acquisition, acquiredOn, transferredOn, gainRupees);
        }

        private static LocalDate acquiredOnOf(TradeOutcomeEntity row) {
            if (row.getBuyDate() == null) {
                throw new IllegalArgumentException("The disposal " + row.getId() + " has no buy date, so when it was"
                        + " acquired is unknown");
            }
            return row.getBuyDate();
        }

        private static LocalDate transferredOnOf(TradeOutcomeEntity row) {
            if (row.getSellDate() == null) {
                throw new IllegalArgumentException("The disposal " + row.getId() + " has no sell date, so when it was"
                        + " transferred is unknown");
            }
            return row.getSellDate();
        }
    }

    /**
     * What a side's breakdown says of securities transaction tax — the condition S2 ss.196 and 198 turn on (D-241). The
     * charge engine writes a line for each charge it levied, so a breakdown without one records none; no breakdown
     * records nothing (D-292).
     */
    private static SttEvidence sttEvidenceOf(Map<String, Double> breakup) {
        if (breakup == null || breakup.isEmpty()) {
            return SttEvidence.UNKNOWN;
        }
        return breakup.getOrDefault(STT, 0.0) > 0 ? SttEvidence.PAID : SttEvidence.NOT_PAID;
    }

    /** {@code trade_outcomes:} and a digest of the rows read, so a later read of changed rows names another run. */
    private static String referenceOf(List<TradeOutcomeEntity> rows) {
        String material = rows.stream()
                .sorted(Comparator.comparing(TradeOutcomeEntity::getId))
                .map(row -> row.getId() + ":" + gainOf(row))
                .collect(Collectors.joining("\n"));
        return "trade_outcomes:" + TradeFingerprint.sha256(material);
    }
}
