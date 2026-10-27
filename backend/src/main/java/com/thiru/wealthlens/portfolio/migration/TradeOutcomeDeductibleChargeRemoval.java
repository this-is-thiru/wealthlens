package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.DeductibleChargeRemovalReport;
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
 * Removes the deductible charges once stored on each {@code trade_outcomes} row — {@code deductible_buy_charges} and
 * {@code deductible_sell_charges} — which no code writes or reads since W-162b: which charges reduce a capital gain is
 * the tax side's rule data for the year (W-162a, D-376), so a stored figure would be a law-derived label.
 *
 * <p>The field names are literals because the entity no longer maps them. Idempotent: only rows still carrying either
 * field are matched, so a second run — including after one that failed part-way — finishes what is left or changes
 * nothing. Run when a {@code SUPER_USER} asks ({@code POST /trade-outcomes/deductible-charges/remove}), never at
 * startup (D-280); the caller is recorded on each row changed. One-time: removed once its run is logged for every
 * database (W-174).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeDeductibleChargeRemoval {

    private static final String BUY = "deductible_buy_charges";
    private static final String SELL = "deductible_sell_charges";

    private final MongoTemplate mongoTemplate;

    /** Remove both fields from every row carrying either, unless {@code dryRun}; {@code auditor} is who asked. */
    public DeductibleChargeRemovalReport removeDeductibleCharges(boolean dryRun, String auditor) {
        Query carrying = Query.query(new Criteria().orOperator(Criteria.where(BUY).exists(true),
                Criteria.where(SELL).exists(true)));
        if (dryRun) {
            return new DeductibleChargeRemovalReport(true, auditor,
                    mongoTemplate.count(carrying, TradeOutcomeEntity.class));
        }
        Update remove = new Update().unset(BUY).unset(SELL)
                .set("audit_metadata.updatedBy", auditor)
                .set("audit_metadata.updatedAt", TLocalDateTime.now());
        long removed = mongoTemplate.updateMulti(carrying, remove, TradeOutcomeEntity.class).getModifiedCount();
        log.info("Removed the stored deductible charges from {} trade outcomes, by {}", removed, auditor);
        return new DeductibleChargeRemovalReport(false, auditor, removed);
    }
}
