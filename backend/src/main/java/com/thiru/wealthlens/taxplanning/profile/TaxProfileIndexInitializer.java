package com.thiru.wealthlens.taxplanning.profile;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

/**
 * Creates TS T7's indexes for the profile collections; {@code auto-index-creation} is off application-wide. The collections are
 * new, so a failure here is not expected duplicates but a broken database, and startup fails loudly rather than run without the
 * PAN's uniqueness.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TaxProfileIndexInitializer {

    private static final List<Class<?>> ENTITIES = List.of(TaxpayerProfile.class, FamilyMember.class, Employer.class,
            Landlord.class, Lender.class, BankAccount.class);

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        for (Class<?> entity : ENTITIES) {
            resolver.resolveIndexFor(entity).forEach(index -> mongoTemplate.indexOps(entity).createIndex(index));
            log.info("Ensured the indexes of {}", entity.getSimpleName());
        }
    }
}
