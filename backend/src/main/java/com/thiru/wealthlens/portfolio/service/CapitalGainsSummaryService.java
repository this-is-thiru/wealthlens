package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * breakdown of them cannot be quantified at all, and refuses the year rather than overstating its gain.
 *
 * <p>Rows are chosen by sell date, not by their stored year, which is written in two forms (W-086a).
 */
@Service
@Log4j2
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CapitalGainsSummaryService {

    private static final Pattern FINANCIAL_YEAR = Pattern.compile("(\\d{4})-(\\d{4})");

    private final TradeOutcomeRepository tradeOutcomeRepository;

    /**
     * The summary of {@code email}'s disposals sold in {@code financialYear}.
     *
     * @param financialYear the year in {@code TLocalDate.financialYear}'s form, such as {@code 2025-2026}
     * @throws IllegalArgumentException if the year is malformed, or a disposal's deductible charges are unknown
     */
    public CapitalGainsSummary summaryFor(String email, String financialYear) {
        int startYear = startYearOf(financialYear);
        List<TradeOutcomeEntity> rows = tradeOutcomeRepository.findByEmailAndSellDateBetween(email,
                LocalDate.of(startYear, 3, 31), LocalDate.of(startYear + 1, 4, 1));
        requireQuantifiable(rows);
        Map<BucketKey, Double> gainByKey = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(TradeOutcomeEntity::getSellDate).thenComparing(TradeOutcomeEntity::getId))
                .forEach(row -> gainByKey.merge(BucketKey.of(row), gainOf(row), TMoney::add));
        List<CapitalGainsBucket> buckets = gainByKey.entrySet().stream()
                .map(entry -> entry.getKey().bucket(entry.getValue()))
                .toList();
        log.debug("Capital-gains summary for {} {}: {} disposals in {} buckets", email, financialYear, rows.size(),
                buckets.size());
        return new CapitalGainsSummary(email, financialYear, buckets, Instant.now(), referenceOf(rows));
    }

    private static int startYearOf(String financialYear) {
        Matcher matcher = FINANCIAL_YEAR.matcher(financialYear == null ? "" : financialYear);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            throw new IllegalArgumentException("A financial year is written as portfolio writes it, such as 2025-2026,"
                    + " but was " + financialYear);
        }
        return Integer.parseInt(matcher.group(1));
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

    /** What a bucket is one of: the rows a bucket sums share all three. */
    private record BucketKey(CapitalGainsType classification, AssetType assetType, Optional<String> subClass) {

        static BucketKey of(TradeOutcomeEntity row) {
            return new BucketKey(row.getCapitalGainsType(), row.getAssetType(),
                    Optional.ofNullable(row.getInstrumentSubClass()));
        }

        CapitalGainsBucket bucket(double gainRupees) {
            return new CapitalGainsBucket(classification, assetType, subClass, gainRupees);
        }
    }

    /** {@code trade_outcomes:} and a digest of the rows read, so a later read of changed rows names another run. */
    private static String referenceOf(List<TradeOutcomeEntity> rows) {
        String material = rows.stream()
                .sorted(Comparator.comparing(TradeOutcomeEntity::getId))
                .map(row -> row.getId() + ":" + row.getCapitalGainsType() + ":" + gainOf(row))
                .collect(Collectors.joining("\n"));
        return "trade_outcomes:" + TradeFingerprint.sha256(material);
    }
}
