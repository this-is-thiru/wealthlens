package com.thiru.wealthlens.taxplanning.documents.store;

import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoredParseAttemptRepository extends MongoRepository<StoredParseAttempt, String> {

    /** Every attempt at any of these documents. */
    List<StoredParseAttempt> findByDocumentIdIn(Collection<String> documentIds);
}
