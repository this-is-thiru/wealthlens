package com.thiru.wealthlens.taxplanning.workspace;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FilingDraftRepository extends MongoRepository<FilingDraft, String> {

    List<FilingDraft> findByWorkspaceId(String workspaceId);

    /** Drafts in {@code status} last changed before {@code updatedBefore}: what auto-archive considers. */
    List<FilingDraft> findByStatusAndAuditMetadataUpdatedAtBefore(DraftStatus status, LocalDateTime updatedBefore);

    /** Archived drafts never acted on, archived before {@code archivedBefore}: what retention deletes on policy alone. */
    List<FilingDraft> findByStatusAndActedOnAtIsNullAndArchivedAtBefore(DraftStatus status, LocalDateTime archivedBefore);

    /** Every draft ever acted on: kept for as long as its year's law lets a loss be carried. */
    List<FilingDraft> findByActedOnAtIsNotNull();

    /** Drafts in any of {@code statuses}: the staleness sweep reads the ready and the filed. */
    List<FilingDraft> findByStatusIn(Collection<DraftStatus> statuses);
}
