package com.thiru.wealthlens.shared.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * On startup, migrates any remaining documents from the legacy
 * {@code temporary_transactions} collection into {@code transactions}
 * with {@code status: "TEMPORARY"}, then drops the old collection.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TempTransactionMigrationRunner implements CommandLineRunner {

    private static final String LEGACY_COLLECTION = "temporary_transactions";
    private static final String TARGET_COLLECTION = "transactions";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        if (!collectionExists(LEGACY_COLLECTION)) {
            log.info("No legacy '{}' collection found — skipping migration", LEGACY_COLLECTION);
            return;
        }

        List<Document> oldDocs = mongoTemplate.find(new Query(), Document.class, LEGACY_COLLECTION);
        if (oldDocs.isEmpty()) {
            log.info("Legacy '{}' collection is empty — dropping it", LEGACY_COLLECTION);
            mongoTemplate.dropCollection(LEGACY_COLLECTION);
            return;
        }

        for (Document doc : oldDocs) {
            doc.put("status", "TEMPORARY");
            doc.remove("_class");
            mongoTemplate.save(doc, TARGET_COLLECTION);
        }

        if (collectionExists(LEGACY_COLLECTION)) {
            mongoTemplate.dropCollection(LEGACY_COLLECTION);
            log.info("Migrated {} temporary transactions and dropped old collection '{}'", oldDocs.size(), LEGACY_COLLECTION);
        }
    }

    private boolean collectionExists(String name) {
        return mongoTemplate.collectionExists(name);
    }
}
