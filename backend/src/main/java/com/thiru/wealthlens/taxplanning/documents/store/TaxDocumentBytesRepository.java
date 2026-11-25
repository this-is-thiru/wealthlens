package com.thiru.wealthlens.taxplanning.documents.store;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxDocumentBytesRepository extends JpaRepository<TaxDocumentBytes, String> {

    /** Rows stored before {@code instant}, whether or not a document still names them (the retention sweep). */
    List<TaxDocumentBytes> findByUploadedAtBefore(LocalDateTime instant);
}
