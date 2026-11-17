package com.thiru.wealthlens.taxplanning.documents;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import java.time.LocalDateTime;

/**
 * An uploaded document as its owner sees it (W-131d1).
 *
 * @param taxYear as {@code 2026-27}
 * @param newlyStored false where the same bytes were already stored under the profile, which is then returned ({@code DR 05} M7)
 */
public record DocumentView(String documentId, String profileId, String taxYear, DocumentType documentType, int pages,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime uploadedAt,
        boolean newlyStored) {

    static DocumentView of(IngestedDocument document, boolean newlyStored) {
        return new DocumentView(document.documentId(), document.profileId(), document.taxYear().label(), document.documentType(),
                document.pageCount(), document.uploadedAt(), newlyStored);
    }
}
