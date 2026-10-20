package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.FinancialYearRewriteReport;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Rewrites the financial year of the {@code trade_outcomes} rows the transaction-based migration wrote as
 * {@code FY2025-26} into the form the rest of {@code portfolio} writes, {@code 2025-2026} (D-239).
 *
 * <p>The year is recomputed from the row's sell date — the fact it was derived from — rather than by reshaping the
 * string. Idempotent: once no {@code FY} row remains there is nothing to rewrite. Run when a {@code SUPER_USER} asks
 * ({@code POST /trade-outcomes/financial-years/rewrite}), never at startup (D-280, D-285); the caller is recorded on
 * each row rewritten, since a bulk update passes no auditing.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeFinancialYearMigration {

    private final MongoTemplate mongoTemplate;

    /**
     * Rewrite every {@code FY} row unless {@code dryRun}; {@code auditor} is who asked.
     *
     * @throws IllegalStateException if an {@code FY} row has no sell date, which the writer could not have produced —
     *     in a dry run too, so it shows before any write
     */
    public FinancialYearRewriteReport rewriteFinancialYears(boolean dryRun, String auditor) {
        Query fyRows = Query.query(Criteria.where("financialYear").regex("^FY"));
        fyRows.fields().include("sellDate");
        List<TradeOutcomeEntity> rows = mongoTemplate.find(fyRows, TradeOutcomeEntity.class);
        if (rows.isEmpty()) {
            return new FinancialYearRewriteReport(dryRun, auditor, 0);
        }
        LocalDateTime now = TLocalDateTime.now();
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TradeOutcomeEntity.class);
        for (TradeOutcomeEntity row : rows) {
            if (row.getSellDate() == null) {
                throw new IllegalStateException("The trade outcome " + row.getId() + " has an FY financial year but"
                        + " no sell date, so its year cannot be recomputed; correct it by hand");
            }
            bulk.updateOne(Query.query(Criteria.where("id").is(row.getId())),
                    Update.update("financialYear", TLocalDate.financialYear(row.getSellDate()))
                            .set("audit_metadata.updatedBy", auditor)
                            .set("audit_metadata.updatedAt", now));
        }
        if (dryRun) {
            return new FinancialYearRewriteReport(true, auditor, rows.size());
        }
        int rewritten = bulk.execute().getModifiedCount();
        log.info("Rewrote the financial year of {} trade outcomes from the FY form, by {}", rewritten, auditor);
        return new FinancialYearRewriteReport(false, auditor, rewritten);
    }
}
