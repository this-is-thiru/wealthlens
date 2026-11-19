package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.portfolio.dto.SegmentCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.SegmentCorrectionReport.UserSegmentCorrection;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Restores the segment that transactions and outcome rows stored before W-292 / W-295 lost, from what stored data
 * records of it (W-297, D-599).
 *
 * <p>Until W-292 no transaction or lot stored its segment, so every one says {@code DELIVERY}; until W-295 a V1 or
 * rebuilt outcome row held no row-level segment. In order:
 * <ol>
 *   <li>a transaction takes the segment its BUY or SELL charge row was priced under, where that is not delivery;</li>
 *   <li>an outcome row's {@code segment} and {@code sell_segment} take its sale transaction's, where that is not
 *       delivery; failing that, an absent {@code segment} takes the row's own {@code sell_segment};</li>
 *   <li>its {@code buy_segment} takes the segment of its holding's buys on its buy date, where they all agree on one
 *       that is not delivery; where they disagree the row is listed and left.</li>
 * </ol>
 * Every rule only moves a stored value to a recorded non-delivery fact, and reads the transactions as step 1 leaves
 * them, so a second run — including after one that failed part-way — finds nothing left. A trade no stored fact
 * records stays as it is. It does not rebuild lots or P&amp;L: each user reported needs §2.12 then §2.11. Run when a
 * {@code SUPER_USER} asks, never at startup (D-280); the caller is recorded on each document written. One-time:
 * removed once its run is logged for every database (W-299).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SegmentCorrection {

    private static final Set<ChargeEvent> TRADE_EVENTS = EnumSet.of(ChargeEvent.BUY, ChargeEvent.SELL);

    private final MongoTemplate mongoTemplate;

    /** Restore what stored data records unless {@code dryRun}; {@code auditor} is who asked. */
    public SegmentCorrectionReport correct(boolean dryRun, String auditor) {
        Map<String, TransactionEntity> transactions = mongoTemplate.findAll(TransactionEntity.class).stream()
                .collect(Collectors.toMap(TransactionEntity::getId, Function.identity()));
        Map<String, TradeSegment> corrected = fromChargeRows(transactions);
        Map<String, TradeSegment> segmentOf = new HashMap<>();
        transactions.forEach((id, trade) -> segmentOf.put(id, corrected.getOrDefault(id, trade.getSegment())));
        Map<String, Set<TradeSegment>> boughtIn = boughtIn(transactions.values(), segmentOf);
        List<RowFix> fixes = mongoTemplate.findAll(TradeOutcomeEntity.class).stream()
                .map(row -> fixOf(row, segmentOf, boughtIn)).toList();
        if (!dryRun) {
            write(corrected, fixes, auditor);
        }
        SegmentCorrectionReport report = report(dryRun, auditor, transactions, corrected, fixes);
        log.info("Segment correction by {} (dry run {}): {} transactions, {} rows, {} rows with mixed buys", auditor,
                dryRun, report.transactionsCorrected(), report.rowsCorrected(), report.rowsWithMixedBuys().size());
        return report;
    }

    /** Step 1: each transaction whose trade charge row records a non-delivery segment it does not hold. */
    private Map<String, TradeSegment> fromChargeRows(Map<String, TransactionEntity> transactions) {
        Map<String, TradeSegment> corrected = new HashMap<>();
        Query tradeCharges = Query.query(Criteria.where("event").in(TRADE_EVENTS));
        for (UserChargeEntity charge : mongoTemplate.find(tradeCharges, UserChargeEntity.class)) {
            TransactionEntity trade = transactions.get(charge.getTransactionId());
            if (trade != null && isRecorded(charge.getSegment()) && charge.getSegment() != trade.getSegment()) {
                corrected.put(trade.getId(), charge.getSegment());
            }
        }
        return corrected;
    }

    /** The segments each holding bought in on each day, as step 1 leaves the transactions. */
    private static Map<String, Set<TradeSegment>> boughtIn(Iterable<TransactionEntity> transactions,
                                                          Map<String, TradeSegment> segmentOf) {
        Map<String, Set<TradeSegment>> boughtIn = new HashMap<>();
        for (TransactionEntity trade : transactions) {
            if (trade.getTransactionType() == TransactionType.BUY) {
                boughtIn.computeIfAbsent(holdingOn(trade.getEmail(), trade.getStockCode(), trade.getBrokerName(),
                        trade.getAccountHolder(), trade.getTransactionDate()), _ -> new HashSet<>())
                        .add(segmentOf.get(trade.getId()));
            }
        }
        return boughtIn;
    }

    /** Steps 2 and 3 for one row: the fields to set, by Java name, and whether its buys disagree. */
    private static RowFix fixOf(TradeOutcomeEntity row, Map<String, TradeSegment> segmentOf,
                                Map<String, Set<TradeSegment>> boughtIn) {
        Map<String, TradeSegment> fields = new LinkedHashMap<>();
        TradeSegment sale = row.getSourceSellTransactionId() == null ? null
                : segmentOf.get(row.getSourceSellTransactionId());
        if (isRecorded(sale)) {
            setIfDifferent(fields, "segment", row.getSegment(), sale);
            setIfDifferent(fields, "sellSegment", row.getSellSegment(), sale);
        } else if (row.getSegment() == null && row.getSellSegment() != null) {
            fields.put("segment", row.getSellSegment());
        }
        Set<TradeSegment> bought = boughtIn.getOrDefault(holdingOn(row.getEmail(), row.getStockCode(),
                row.getBrokerName(), row.getAccountHolder(), row.getBuyDate()), Set.of());
        if (bought.size() == 1 && isRecorded(bought.iterator().next())) {
            setIfDifferent(fields, "buySegment", row.getBuySegment(), bought.iterator().next());
        }
        return new RowFix(row, fields, bought.size() > 1);
    }

    private void write(Map<String, TradeSegment> corrected, List<RowFix> fixes, String auditor) {
        LocalDateTime now = TLocalDateTime.now();
        corrected.forEach((id, segment) -> mongoTemplate.updateFirst(byId(id),
                audited(Update.update("segment", segment), auditor, now), TransactionEntity.class));
        for (RowFix fix : fixes) {
            if (!fix.fields().isEmpty()) {
                Update update = new Update();
                fix.fields().forEach(update::set);
                mongoTemplate.updateFirst(byId(fix.row().getId()), audited(update, auditor, now),
                        TradeOutcomeEntity.class);
            }
        }
    }

    private static SegmentCorrectionReport report(boolean dryRun, String auditor,
                                                  Map<String, TransactionEntity> transactions,
                                                  Map<String, TradeSegment> corrected, List<RowFix> fixes) {
        Map<String, long[]> byUser = new TreeMap<>();
        corrected.keySet().forEach(id -> byUser.computeIfAbsent(transactions.get(id).getEmail(), _ -> new long[2])[0]++);
        List<RowFix> correctedRows = fixes.stream().filter(fix -> !fix.fields().isEmpty()).toList();
        correctedRows.forEach(fix -> byUser.computeIfAbsent(fix.row().getEmail(), _ -> new long[2])[1]++);
        List<UserSegmentCorrection> users = byUser.entrySet().stream()
                .map(user -> new UserSegmentCorrection(user.getKey(), user.getValue()[0], user.getValue()[1])).toList();
        List<String> mixed = fixes.stream().filter(RowFix::mixedBuys).map(fix -> fix.row().getId()).sorted().toList();
        return new SegmentCorrectionReport(dryRun, auditor, corrected.size(), correctedRows.size(), users, mixed);
    }

    /** A segment stored data records as traded: anything but the delivery every unrecorded trade holds. */
    private static boolean isRecorded(TradeSegment segment) {
        return segment != null && segment != TradeSegment.DELIVERY;
    }

    private static void setIfDifferent(Map<String, TradeSegment> fields, String field, TradeSegment stored,
                                       TradeSegment recorded) {
        if (stored != recorded) {
            fields.put(field, recorded);
        }
    }

    private static String holdingOn(String email, String stockCode, BrokerName brokerName, String accountHolder,
                                    LocalDate date) {
        return email + "|" + stockCode + "|" + brokerName + "|" + accountHolder + "|" + date;
    }

    private static Query byId(String id) {
        return Query.query(Criteria.where("id").is(id));
    }

    private static Update audited(Update update, String auditor, LocalDateTime now) {
        // The Java name, so the actor is stored through its converter; the stored name would bypass it (W-256).
        return update.set("auditMetadata.updatedBy", auditor).set("audit_metadata.updatedAt", now);
    }

    /** One outcome row's segments to set, by Java name, and whether its buys that day disagree. */
    private record RowFix(TradeOutcomeEntity row, Map<String, TradeSegment> fields, boolean mixedBuys) {
    }
}
