package com.thiru.wealthlens.taxplanning.documents.store;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredParseAttemptRepository extends JpaRepository<StoredParseAttempt, String> {

    /** Every attempt at any of these documents. */
    List<StoredParseAttempt> findByDocumentIdIn(Collection<String> documentIds);
}
