package com.thiru.wealthlens.taxplanning.documents.store;

import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, String> {

    /** The document a profile holds with these bytes, if any ({@code DR 05} M7). */
    java.util.Optional<StoredDocument> findByProfileIdAndSha256(String profileId, String sha256);

    /** Every document a profile holds. */
    List<StoredDocument> findByProfileId(String profileId);

    /** Every document of the forms some parser reads, for the parse job. */
    List<StoredDocument> findByDocumentTypeIn(Collection<DocumentType> types);

    /** Every document of one tax year, whichever profile holds it, for retention. */
    List<StoredDocument> findByTaxYearStart(int taxYearStart);

    /** Each tax year some document is held for, for retention. */
    @Query("SELECT DISTINCT d.taxYearStart FROM StoredDocument d")
    List<Integer> findTaxYearStarts();
}
