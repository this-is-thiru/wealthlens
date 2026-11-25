package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DraftRevisionRepository extends JpaRepository<DraftRevision, String> {

    List<DraftRevision> findByDraftIdOrderByRevisionNumber(String draftId);

    Page<DraftRevision> findByDraftId(String draftId, Pageable pageable);

    void deleteByDraftId(String draftId);

    /** The revision that imported a workbook of this SHA-256 into the draft, if one did (W-133b C6a). */
    Optional<DraftRevision> findFirstByDraftIdAndImportedFromWorkbookSha256(String draftId, String workbookSha256);

    /**
     * Ends any wait of this transaction on another's uncommitted write after {@code timeout}, in a lock timeout, for
     * the rest of the transaction only (W-337f, D-621 C2a).
     */
    @Query(value = "SELECT set_config('lock_timeout', :timeout, true)", nativeQuery = true)
    String boundLockWaits(@Param("timeout") String timeout);
}
