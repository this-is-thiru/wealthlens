package com.thiru.wealthlens.taxplanning.documents.store;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoredDocumentRepository extends MongoRepository<StoredDocument, String> {

    /** Every document a profile holds. */
    List<StoredDocument> findByProfileId(String profileId);

    /** Every document of one tax year, whichever profile holds it, for retention. */
    List<StoredDocument> findByTaxYearStart(int taxYearStart);
}
