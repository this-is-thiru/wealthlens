package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
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
 * string. Idempotent: once no {@code FY} row remains there is nothing to rewrite. Triggered at startup by
 * {@link com.thiru.wealthlens.shared.config.TradeOutcomeFinancialYearMigrationRunner}.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeFinancialYearMigration {

    private final MongoTemplate mongoTemplate;

    /**
     * Rewrite every {@code FY} row; how many were rewritten.
     *
     * @throws IllegalStateException if an {@code FY} row has no sell date, which the writer could not have produced
     */
    public int rewriteFinancialYears() {
        Query fyRows = Query.query(Criteria.where("financialYear").regex("^FY"));
        fyRows.fields().include("sellDate");
        List<TradeOutcomeEntity> rows = mongoTemplate.find(fyRows, TradeOutcomeEntity.class);
        if (rows.isEmpty()) {
            return 0;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TradeOutcomeEntity.class);
        for (TradeOutcomeEntity row : rows) {
            if (row.getSellDate() == null) {
                throw new IllegalStateException("The trade outcome " + row.getId() + " has an FY financial year but"
                        + " no sell date, so its year cannot be recomputed; correct it by hand");
            }
            bulk.updateOne(Query.query(Criteria.where("id").is(row.getId())),
                    Update.update("financialYear", TLocalDate.financialYear(row.getSellDate())));
        }
        int rewritten = bulk.execute().getModifiedCount();
        log.info("Rewrote the financial year of {} trade outcomes from the FY form", rewritten);
        return rewritten;
    }
}
