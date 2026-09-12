package com.thiru.wealthlens.portfolio.config;

import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
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

    private static final List<Class<?>> PORTFOLIO_ENTITIES = List.of(ProfitAndLossEntity.class, TradeOutcomeEntity.class);

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver =
                new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());

        for (Class<?> entity : PORTFOLIO_ENTITIES) {
            for (IndexDefinition index : resolver.resolveIndexFor(entity)) {
                mongoTemplate.indexOps(entity).createIndex(index);
                log.debug("Ensured index on {}: {}", entity.getSimpleName(), index.getIndexKeys());
            }
        }
    }
}
