package com.thiru.wealthlens.portfolio.config;

import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * Creates the declared indexes for portfolio entities.
 *
 * <p>{@code auto-index-creation} is off application-wide, so an {@code @Indexed} or
 * {@code @CompoundIndex} annotation is documentation that creates nothing. That is the same reason
 * {@code ChargeIndexInitializer} exists; this is its portfolio counterpart, and it exists because
 * {@code findByEmailAndFinancialYear} runs on every buy and every sell and was scanning the whole
 * collection to do it.
 *
 * <p>{@code createIndex} is idempotent in MongoDB, so this is a no-op on every start after the
 * first.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PortfolioIndexInitializer {

    private static final List<Class<?>> PORTFOLIO_ENTITIES = List.of(ProfitAndLossEntity.class, TradeOutcomeEntity.class, HoldingPeriodPolicyEntity.class,
                    TransactionEntity.class);

    private final MongoTemplate mongoTemplate;

    /**
     * Creating an index can fail, and a failure must not stop the application starting.
     *
     * <p>The realistic case is a <b>unique</b> index over data that already contains duplicates —
     * {@code transactions} has carried {@code @Indexed(unique = true, sparse = true)} on
     * {@code source_temp_transaction_id} for some time, but {@code auto-index-creation} is off and
     * this class did not cover the collection, so the constraint has never actually existed. Adding
     * the collection here applies it for the first time, against data written without it.
     *
     * <p>Failing startup over that would take the application down on deploy. Continuing silently
     * would be worse in a different way: the constraint would be believed to exist when it does
     * not. So it is loud, it names the collection and the keys, and the application starts.
     */
    private void createIndex(Class<?> entity, IndexDefinition index) {
        try {
            mongoTemplate.indexOps(entity).createIndex(index);
            log.debug("Ensured index on {}: {}", entity.getSimpleName(), index.getIndexKeys());
        } catch (RuntimeException e) {
            log.error("Could not create index {} on {}. The application has started WITHOUT this"
                            + " constraint, so anything relying on it is unenforced. If it is unique,"
                            + " the collection almost certainly already holds duplicates: find them,"
                            + " resolve them, and restart. Cause: {}",
                    index.getIndexKeys(), entity.getSimpleName(), e.getMessage());
        }
    }

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver =
                new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());

        for (Class<?> entity : PORTFOLIO_ENTITIES) {
            for (IndexDefinition index : resolver.resolveIndexFor(entity)) {
                createIndex(entity, index);
            }
        }
    }
}
