package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftRevisionRepository extends MongoRepository<DraftRevision, String> {

    List<DraftRevision> findByDraftIdOrderByRevisionNumber(String draftId);

    Page<DraftRevision> findByDraftId(String draftId, Pageable pageable);

    void deleteByDraftId(String draftId);

    /** The revision that imported a workbook of this SHA-256 into the draft, if one did (W-133b C6a). */
    Optional<DraftRevision> findFirstByDraftIdAndImportedFromWorkbookSha256(String draftId, String workbookSha256);
}
