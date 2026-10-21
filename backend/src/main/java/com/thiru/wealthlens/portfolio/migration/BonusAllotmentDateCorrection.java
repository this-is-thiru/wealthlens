package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentCorrectionReport.ActionCorrection;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentDate;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.TradeClassification;
import com.thiru.wealthlens.portfolio.holding.TradeClassificationQuery;
import com.thiru.wealthlens.portfolio.holding.TradeClassifier;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Re-dates the bonus lots processed before W-088k4a from their action's ex-date to its allotment date, which the owner
 * supplies from each company's announcement — a bonus share's holding period runs from its allotment (1961 Explanation
 * 1(i)(f) to s.2(42A); 2025 s.2(101)(c)(C)(IV); W-088k4b, D-308).
 *
 * <p>For each action it records the allotment date, then moves the bonus lots, their transactions and the disposals of
 * those lots off the ex-date, recomputing each disposal's holding period and {@code portfolio}'s own classification.
 * Only documents still at the ex-date are touched, so a second run — including after one that failed part-way — finds
 * nothing left. Every entry is validated before anything is written. Run when a {@code SUPER_USER} asks, never at
 * startup (D-280); the caller is recorded on each document written. One-time: removed once its run is logged for
 * every database (W-150).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class BonusAllotmentDateCorrection {

    private final MongoTemplate mongoTemplate;

    private final TradeClassifier tradeClassifier;

    /**
     * Correct the given actions unless {@code dryRun}; {@code auditor} is who asked.
     *
     * @throws IllegalArgumentException if an entry is incomplete or repeated, names no action or one that is not a
     *     bonus, or gives a date other than the one the action already holds — before any write
     */
    public BonusAllotmentCorrectionReport correct(List<BonusAllotmentDate> entries, boolean dryRun, String auditor) {
        List<CorporateActionEntity> actions = validated(entries);
        LocalDateTime now = TLocalDateTime.now();
        List<ActionCorrection> corrections = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            corrections.add(correct(actions.get(i), entries.get(i).allotmentDate(), dryRun, auditor, now));
        }
        log.info("Bonus allotment-date correction by {} (dry run {}): {}", auditor, dryRun, corrections);
        return new BonusAllotmentCorrectionReport(dryRun, auditor, corrections);
    }

    private List<CorporateActionEntity> validated(List<BonusAllotmentDate> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Name at least one bonus action and its allotment date");
        }
        Set<String> seen = new HashSet<>();
        List<CorporateActionEntity> actions = new ArrayList<>();
        for (BonusAllotmentDate entry : entries) {
            if (entry == null || entry.corporateActionId() == null || entry.allotmentDate() == null) {
                throw new IllegalArgumentException("Each entry needs a corporateActionId and an allotmentDate: " + entry);
            }
            if (!seen.add(entry.corporateActionId())) {
                throw new IllegalArgumentException("The action " + entry.corporateActionId() + " is named twice");
            }
            CorporateActionEntity action = Optional.ofNullable(
                    mongoTemplate.findById(entry.corporateActionId(), CorporateActionEntity.class))
                    .orElseThrow(() -> new IllegalArgumentException("No corporate action " + entry.corporateActionId()));
            if (action.getType() != CorporateActionType.BONUS) {
                throw new IllegalArgumentException("The action " + action.getId() + " is a " + action.getType()
                        + ", not a bonus; only a bonus share's holding period runs from its allotment");
            }
            if (action.getAllotmentDate() != null && !action.getAllotmentDate().equals(entry.allotmentDate())) {
                throw new IllegalArgumentException("The bonus " + action.getId() + " already holds the allotment date "
                        + action.getAllotmentDate() + ", not " + entry.allotmentDate() + "; correct it by hand");
            }
            actions.add(action);
        }
        return actions;
    }

    private ActionCorrection correct(CorporateActionEntity action, LocalDate allotmentDate, boolean dryRun,
            String auditor, LocalDateTime now) {
        Query lots = bonusDocuments(action);
        Query disposals = Query.query(Criteria.where("acquisitionCorporateAction").is(CorporateActionType.BONUS)
                .and("stockCode").is(action.getStockCode()).and("buyDate").is(action.getExDate()));
        List<TradeOutcomeEntity> rows = mongoTemplate.find(disposals, TradeOutcomeEntity.class);
        ActionCorrection found = new ActionCorrection(action.getId(), action.getStockCode(), action.getExDate(),
                allotmentDate, mongoTemplate.count(lots, AssetEntity.class),
                mongoTemplate.count(lots, TransactionEntity.class), rows.size());
        if (dryRun) {
            return found;
        }
        Update redate = audited(Update.update("transactionDate", allotmentDate), auditor, now);
        mongoTemplate.updateMulti(lots, redate, AssetEntity.class);
        mongoTemplate.updateMulti(lots, redate, TransactionEntity.class);
        rows.forEach(row -> mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(row.getId())),
                audited(redated(row, allotmentDate), auditor, now), TradeOutcomeEntity.class));
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(action.getId())),
                audited(Update.update("allotmentDate", allotmentDate), auditor, now), CorporateActionEntity.class);
        return found;
    }

    /** The action's bonus lots — or their transactions — still at its ex-date; each carries a copy of the action. */
    private static Query bonusDocuments(CorporateActionEntity action) {
        return Query.query(Criteria.where("corporateActionType").is(CorporateActionType.BONUS)
                .and("transactionDate").is(action.getExDate())
                .and("corporateActions.id").is(action.getId()));
    }

    /** The disposal's buy date moved, and what portfolio derived from it recomputed from the new date. */
    private Update redated(TradeOutcomeEntity row, LocalDate allotmentDate) {
        // A row from before segments were kept was classified with the entry default, DELIVERY (D-295).
        TradeSegment segment = Optional.ofNullable(row.getSegment()).orElse(TradeSegment.DELIVERY);
        TradeClassification classification = tradeClassifier.classify(new TradeClassificationQuery(row.getStockCode(),
                row.getAssetType(), segment, allotmentDate, row.getSellDate()));
        return Update.update("buyDate", allotmentDate)
                .set("holdingPeriodDays", ChronoUnit.DAYS.between(allotmentDate, row.getSellDate()))
                .set("capitalGainsType", classification.capitalGainsType())
                .set("classificationReason", classification.reason());
    }

    private static Update audited(Update update, String auditor, LocalDateTime now) {
        return update.set("audit_metadata.updatedBy", auditor).set("audit_metadata.updatedAt", now);
    }
}
