package com.thiru.wealthlens.brokercharges.config;

import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
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
 * Creates the indexes declared on the charges entities.
 *
 * <h2>Why this class has to exist</h2>
 * Spring Data MongoDB has defaulted {@code auto-index-creation} to false since 3.0, and this
 * application does not enable it. Left alone, {@code @Indexed} and {@code @CompoundIndex} are
 * documentation that creates nothing — the unique index meant to make a re-uploaded quarter
 * idempotent would not exist, and every deduplication lookup would be a collection scan.
 *
 * <p>Enabling the setting globally would instead build indexes for every entity in the application
 * on startup, including collections this module knows nothing about. So the annotations stay the
 * single declaration of intent, and this applies them for the charges collections only.
 *
 * <p>{@code createIndex} is idempotent in MongoDB, so this is a no-op on every start after the
 * first.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class ChargeIndexInitializer {

    private static final List<Class<?>> CHARGE_ENTITIES = List.of(
            ChargeScheduleEntity.class,
            ChargeInstrumentEntity.class,
            UserChargeEntity.class,
            ChargeCatalogueEntity.class,
            ChargeAccountEntity.class);

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());

        for (Class<?> entity : CHARGE_ENTITIES) {
            for (IndexDefinition index : resolver.resolveIndexFor(entity)) {
                try {
                    mongoTemplate.indexOps(entity).createIndex(index);
                } catch (RuntimeException e) {
                    // An index that already exists under a different definition should be visible,
                    // but must not stop the application from starting.
                    log.warn("Could not create index {} on {}: {}",
                            index.getIndexKeys(), entity.getSimpleName(), e.getMessage());
                }
            }
            log.info("Charge indexes ensured for {}", entity.getSimpleName());
        }
    }
}
