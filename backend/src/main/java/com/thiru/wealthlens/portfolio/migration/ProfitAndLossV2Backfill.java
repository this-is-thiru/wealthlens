package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.V2BackfillReport;
import com.thiru.wealthlens.portfolio.dto.V2BackfillReport.PlacedRow;
import com.thiru.wealthlens.portfolio.dto.V2BackfillReport.UnplacedRow;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Adds each V2 sale's profit, brokerage and misc — which the V2 P&L did not record before W-169 — into the P&L split,
 * from the trade-outcome row {@code TradeOutcomeRecorder} wrote for it (D-372 C2a, W-172).
 *
 * <p>A recorder row is the one carrying {@code sell_charge_breakup}, even an empty one; V1 and the rebuild write none.
 * Only rows written before {@code recordedBefore} (the deploy) are taken: later sales are recorded in full; a row with
 * no record of when it was written is listed rather than guessed. The row's
 * figures are added to the split entry of its asset type and segment, at the year, its month and its half, with the
 * row's id added to the document's {@code pnl_backfilled_outcome_ids} in the same update, conditioned on the id not
 * being there yet — so a row is never counted twice, and a second run, including after one that failed part-way, adds
 * only what is left. A row whose document, side, split entry, month or half does not exist is listed, not written
 * (C4a). Run when a {@code SUPER_USER} asks ({@code POST /profit-and-loss/v2-figures/backfill}), never at startup
 * (D-280). One-time: removed, and the id sets unset, once its run is logged for every database (W-173).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ProfitAndLossV2Backfill {

    private static final String COLLECTION = "profit_and_loss";
    private static final String COUNTED = "pnl_backfilled_outcome_ids";
    private static final String SPLIT = "gains_by_asset_and_segment";
    private static final int FORTNIGHT_BOUNDARY = 15;

    private final MongoTemplate mongoTemplate;

    /** Add every uncounted recorder row written before {@code recordedBefore}, unless {@code dryRun}. */
    public V2BackfillReport backfill(boolean dryRun, LocalDateTime recordedBefore, String auditor) {
        List<PlacedRow> placed = new ArrayList<>();
        List<UnplacedRow> unplaced = new ArrayList<>();
        int alreadyCounted = 0;
        int writtenAfter = 0;
        Query recorderRows = Query.query(Criteria.where("sellChargeBreakup").exists(true)).with(Sort.by("id"));
        for (TradeOutcomeEntity row : mongoTemplate.find(recorderRows, TradeOutcomeEntity.class)) {
            LocalDateTime written = row.getAuditMetadata() == null ? null : row.getAuditMetadata().getCreatedAt();
            if (written != null && !written.isBefore(recordedBefore)) {
                writtenAfter++;
                continue;
            }
            Target target = written == null ? Target.refused("the row records no time it was written")
                    : targetOf(row);
            if (target.reason().isPresent()) {
                unplaced.add(new UnplacedRow(row.getId(), row.getEmail(), target.reason().get()));
            } else if (target.counted()) {
                alreadyCounted++;
            } else {
                if (!dryRun) {
                    write(target, row, auditor);
                }
                placed.add(new PlacedRow(row.getId(), row.getEmail(), target.financialYear(), target.side(),
                        row.getAssetType(), segmentOf(row)));
            }
        }
        log.info("V2 P&L backfill by {} (dry run {}, before {}): {} placed, {} left, {} already counted, {} after the"
                + " cut-off", auditor, dryRun, recordedBefore, placed.size(), unplaced.size(), alreadyCounted, writtenAfter);
        return new V2BackfillReport(dryRun, auditor, recordedBefore, placed, unplaced, alreadyCounted, writtenAfter);
    }

    /** Where the row's figures go, or why there is nowhere. */
    private Target targetOf(TradeOutcomeEntity row) {
        if (row.getAssetType() == null || row.getSellDate() == null) {
            return Target.refused("the row records no asset type or sell date");
        }
        String year = FinancialYear.containing(row.getSellDate()).label();
        String side = row.getAccountType() == AccountType.SELF ? "realised_profits" : "out_sourced_realised_profits";
        Document pnl = mongoTemplate.findOne(Query.query(Criteria.where("email").is(row.getEmail())
                .and("financial_year").is(year)), Document.class, COLLECTION);
        if (pnl == null) {
            return Target.refused("no P&L document for " + year);
        }
        if (pnl.getList(COUNTED, String.class, List.of()).contains(row.getId())) {
            return new Target(pnl, year, side, null, true, Optional.empty());
        }
        Document entry = Optional.ofNullable(pnl.get(side, Document.class))
                .map(realised -> realised.get(SPLIT, Document.class))
                .map(split -> split.get(row.getAssetType().name(), Document.class))
                .map(bySegment -> bySegment.get(segmentOf(row).name(), Document.class))
                .orElse(null);
        if (entry == null) {
            return Target.refused("no " + side + " split entry " + row.getAssetType() + "/" + segmentOf(row)
                    + " in " + year);
        }
        Document month = Optional.ofNullable(entry.get("capital_gains", Document.class))
                .map(months -> months.get(row.getSellDate().getMonth().name(), Document.class)).orElse(null);
        if (month == null || month.get(halfOf(row), Document.class) == null) {
            return Target.refused("no " + row.getSellDate().getMonth() + " half-month entry in " + year);
        }
        return new Target(pnl, year, side, entry, false, Optional.empty());
    }

    /** Adds the row's three figures to its entry, month and half, and counts it, in one conditioned update. */
    private void write(Target target, TradeOutcomeEntity row, String auditor) {
        Document months = new Document(row.getSellDate().getMonth().name(), threeFigures(row)
                .append("month", row.getSellDate().getMonth().name()).append(halfOf(row), threeFigures(row)));
        Document sale = threeFigures(row).append("capital_gains", months);
        String path = target.side() + "." + SPLIT + "." + row.getAssetType() + "." + segmentOf(row);
        Update update = new Update().set(path, StoredReport.sum(target.entry(), sale)).addToSet(COUNTED, row.getId())
                .set("audit_metadata.updatedBy", auditor).set("audit_metadata.updatedAt", TLocalDateTime.now());
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(target.pnl().get("_id"))
                .and(COUNTED).ne(row.getId())), update, COLLECTION);
    }

    /** Profit, brokerage and misc as {@code recordSale} folds them (W-169); purchase and sell are recorded already. */
    private static Document threeFigures(TradeOutcomeEntity row) {
        return new Document("profit", row.getNetProfit())
                .append("brokerage", row.getBuyBrokerCharges() + row.getSellBrokerCharges())
                .append("misc_charges", row.getBuyMiscCharges() + row.getSellMiscCharges());
    }

    private static String halfOf(TradeOutcomeEntity row) {
        return row.getSellDate().getDayOfMonth() <= FORTNIGHT_BOUNDARY ? "first_half_report" : "second_half_report";
    }

    /** The sale's segment, else the row's, else the entry default (D-295). */
    private static TradeSegment segmentOf(TradeOutcomeEntity row) {
        return Optional.ofNullable(row.getSellSegment()).or(() -> Optional.ofNullable(row.getSegment()))
                .orElse(TradeSegment.DELIVERY);
    }

    private record Target(Document pnl, String financialYear, String side, Document entry, boolean counted,
            Optional<String> reason) {

        static Target refused(String reason) {
            return new Target(null, null, null, null, false, Optional.of(reason));
        }
    }
}
