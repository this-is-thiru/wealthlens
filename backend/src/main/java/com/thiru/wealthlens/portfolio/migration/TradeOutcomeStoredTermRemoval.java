package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.StoredTermRemovalReport;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Removes the term {@code portfolio} once stored on each {@code trade_outcomes} row — {@code capital_gains_type} and
 * {@code classification_reason} — which no code writes or reads since W-160c: the term is the tax side's to decide from
 * the row's facts (D-362).
 *
 * <p>The field names are literals because the entity no longer maps them. Idempotent: only rows still carrying either
 * field are matched, so a second run — including after one that failed part-way — finishes what is left or changes
 * nothing. Run when a {@code SUPER_USER} asks ({@code POST /trade-outcomes/stored-terms/remove}), never at startup
 * (D-280); the caller is recorded on each row changed. One-time: removed once its run is logged for every database
 * (W-168).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeStoredTermRemoval {

    private static final String TERM = "capital_gains_type";
    private static final String REASON = "classification_reason";

    private final MongoTemplate mongoTemplate;

    /** Remove both fields from every row carrying either, unless {@code dryRun}; {@code auditor} is who asked. */
    public StoredTermRemovalReport removeStoredTerms(boolean dryRun, String auditor) {
        Query carrying = Query.query(new Criteria().orOperator(Criteria.where(TERM).exists(true),
                Criteria.where(REASON).exists(true)));
        if (dryRun) {
            return new StoredTermRemovalReport(true, auditor,
                    mongoTemplate.count(carrying, TradeOutcomeEntity.class));
        }
        Update remove = new Update().unset(TERM).unset(REASON)
                .set("audit_metadata.updatedBy", auditor)
                .set("audit_metadata.updatedAt", TLocalDateTime.now());
        long removed = mongoTemplate.updateMulti(carrying, remove, TradeOutcomeEntity.class).getModifiedCount();
        log.info("Removed the stored term from {} trade outcomes, by {}", removed, auditor);
        return new StoredTermRemovalReport(false, auditor, removed);
    }
}
