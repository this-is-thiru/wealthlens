package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.RebuildChargeCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.RebuildChargeCorrectionReport.SkippedUser;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.util.money.TMoney;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Corrects the {@code trade_outcomes} rows an earlier startup rebuild wrote for a lot consumed by more than one sell,
 * whose buy charge was re-divided on every later sell (W-088u6, D-276). Each user's rows are recomputed from their
 * transactions through the rebuild itself, now fixed, and only the five figures that derive from the buy charge are
 * set. A user whose rows no longer pair with that recomputation — transactions edited or deleted since — is left as
 * stored and reported, never guessed at. Rows V1 and V2 wrote carry the asset's id, not {@code lot-}, and are never
 * read. Idempotent: once corrected, the recomputation agrees and nothing changes.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class RebuildBuyChargeCorrection {

    /** The synthetic lot id only the rebuild writes ({@code TradeMatchingService.lotOf}). */
    private static final String REBUILD_LOT_ID = "^lot-";

    private final MongoTemplate mongoTemplate;
    private final TransactionBasedTradeOutcomeMigration rebuild;

    /** Find, and unless {@code dryRun} write, the corrections; who asked is recorded on every row written. */
    public RebuildChargeCorrectionReport correct(boolean dryRun, String auditor) {
        List<TradeOutcomeEntity> rebuildRows = mongoTemplate.find(
                Query.query(Criteria.where("source_buy_lot_id").regex(REBUILD_LOT_ID)), TradeOutcomeEntity.class);
        Map<String, List<TradeOutcomeEntity>> byUser = rebuildRows.stream()
                .collect(Collectors.groupingBy(TradeOutcomeEntity::getEmail, TreeMap::new, Collectors.toList()));
        List<Correction> examined = new ArrayList<>();
        List<SkippedUser> skipped = new ArrayList<>();
        byUser.forEach((email, stored) -> {
            if (lotsSoldMoreThanOnce(stored).isEmpty()) {
                return;
            }
            Pairing pairing = pair(stored, rebuild.rebuiltRows(email));
            pairing.refusal().ifPresentOrElse(reason -> skipped.add(new SkippedUser(email, reason)),
                    () -> examined.addAll(pairing.corrections()));
        });
        List<Correction> changed = examined.stream().filter(Correction::changes).toList();
        if (!dryRun && !changed.isEmpty()) {
            write(changed, auditor);
        }
        log.info("Rebuild buy-charge correction (dryRun={}): {} rows examined, {} {}, {} users skipped", dryRun,
                examined.size(), changed.size(), dryRun ? "would change" : "corrected", skipped.size());
        return new RebuildChargeCorrectionReport(dryRun, auditor, examined.size(), changed.size(),
                charged(examined, Correction::stored), charged(examined, Correction::rebuilt), skipped);
    }

    /** One stored row and the same row as the rebuild now makes it. */
    record Correction(TradeOutcomeEntity stored, TradeOutcomeEntity rebuilt) {

        /** Whether any figure that derives from the buy charge differs. */
        boolean changes() {
            return Double.compare(stored.getBuyBrokerCharges(), rebuilt.getBuyBrokerCharges()) != 0
                    || Double.compare(stored.getBuyMiscCharges(), rebuilt.getBuyMiscCharges()) != 0
                    || Double.compare(stored.getTotalBuyValue(), rebuilt.getTotalBuyValue()) != 0
                    || Double.compare(stored.getNetProfit(), rebuilt.getNetProfit()) != 0
                    || Double.compare(stored.getProfitPercentage(), rebuilt.getProfitPercentage()) != 0;
        }
    }

    /** A user's corrections for the rows on lots sold more than once, or, when the rows do not pair, why not. */
    record Pairing(List<Correction> corrections, Optional<String> refusal) {

        static Pairing paired(List<Correction> corrections) {
            return new Pairing(List.copyOf(corrections), Optional.empty());
        }

        static Pairing refused(String reason) {
            return new Pairing(List.of(), Optional.of(reason));
        }
    }

    /**
     * Pairs every stored rebuild row of one user with the rebuild's row for the same sell and lot. The recomputation
     * is kept to the sells the stored rows name, since a later sell V1 or V2 recorded has its own row. Refused unless
     * the two sets of rows match one for one, with the same quantity, so a changed history is never corrected.
     */
    static Pairing pair(List<TradeOutcomeEntity> stored, List<TradeOutcomeEntity> rebuilt) {
        Set<String> sells = stored.stream().map(TradeOutcomeEntity::getSourceSellTransactionId)
                .collect(Collectors.toSet());
        Map<String, TradeOutcomeEntity> rebuiltByKey = new HashMap<>();
        for (TradeOutcomeEntity row : rebuilt) {
            if (sells.contains(row.getSourceSellTransactionId()) && rebuiltByKey.put(key(row), row) != null) {
                return Pairing.refused("the rebuild makes two rows for " + key(row));
            }
        }
        Set<String> soldMoreThanOnce = lotsSoldMoreThanOnce(stored);
        Set<String> seen = new HashSet<>();
        List<Correction> corrections = new ArrayList<>();
        for (TradeOutcomeEntity row : stored) {
            TradeOutcomeEntity match = rebuiltByKey.get(key(row));
            if (!seen.add(key(row)) || match == null
                    || !Objects.equals(row.getBuyQuantity(), match.getBuyQuantity())) {
                return Pairing.refused("the stored row for " + key(row)
                        + " has no single rebuilt row of the same quantity; the transactions changed since the rebuild");
            }
            if (soldMoreThanOnce.contains(lotKey(row))) {
                corrections.add(new Correction(row, match));
            }
        }
        if (seen.size() != rebuiltByKey.size()) {
            return Pairing.refused("the rebuild makes " + rebuiltByKey.size() + " rows for these sells, but "
                    + seen.size() + " are stored; the transactions changed since the rebuild");
        }
        return Pairing.paired(corrections);
    }

    /** The lots — by email, broker, holder and the lot id, which alone is not unique — with two or more rows. */
    private static Set<String> lotsSoldMoreThanOnce(List<TradeOutcomeEntity> rows) {
        return rows.stream().collect(Collectors.groupingBy(RebuildBuyChargeCorrection::lotKey, Collectors.counting()))
                .entrySet().stream().filter(lot -> lot.getValue() > 1).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static String lotKey(TradeOutcomeEntity row) {
        return row.getEmail() + "|" + row.getBrokerName() + "|" + row.getAccountHolder() + "|" + row.getSourceBuyLotId();
    }

    private static String key(TradeOutcomeEntity row) {
        return lotKey(row) + "|" + row.getSourceSellTransactionId();
    }

    private void write(List<Correction> changed, String auditor) {
        LocalDateTime now = TLocalDateTime.now();
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TradeOutcomeEntity.class);
        for (Correction correction : changed) {
            TradeOutcomeEntity rebuilt = correction.rebuilt();
            bulk.updateOne(Query.query(Criteria.where("id").is(correction.stored().getId())), new Update()
                    .set("buy_broker_charges", rebuilt.getBuyBrokerCharges())
                    .set("buy_misc_charges", rebuilt.getBuyMiscCharges())
                    .set("total_buy_value", rebuilt.getTotalBuyValue())
                    .set("net_profit", rebuilt.getNetProfit())
                    .set("profit_percentage", rebuilt.getProfitPercentage())
                    .set("audit_metadata.updatedBy", auditor)
                    .set("audit_metadata.updatedAt", now));
        }
        int written = bulk.execute().getModifiedCount();
        log.info("Corrected the buy charges of {} rebuild trade outcomes", written);
    }

    private static double charged(List<Correction> corrections, Function<Correction, TradeOutcomeEntity> side) {
        double total = 0;
        for (Correction correction : corrections) {
            total += side.apply(correction).getBuyBrokerCharges() + side.apply(correction).getBuyMiscCharges();
        }
        return TMoney.scale(total);
    }
}
