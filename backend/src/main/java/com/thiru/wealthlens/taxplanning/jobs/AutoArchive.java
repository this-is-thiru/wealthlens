package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Archives a {@code DRAFT} nobody has changed for {@link RetentionPeriods#archiveAfter()}, unless it is its workspace's primary
 * ({@code DR 11} F6). Reversible: the user restores it.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class AutoArchive implements TaxJob {

    private final FilingDraftRepository drafts;
    private final TaxYearWorkspaceRepository workspaces;
    private final RetentionPeriods periods;
    private final TaxpayerProfileRepository profiles;

    @Override
    public String name() {
        return "auto-archive";
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(30);
    }

    @Override
    public void run(JobContext context) {
        // audit_metadata.updated_at is written in the system's zone by Spring Data auditing, so the cutoff is too
        LocalDateTime cutoff = LocalDateTime.now(context.clock()).minus(periods.archiveAfter());
        List<FilingDraft> untouched = drafts.findByStatusAndAuditMetadataUpdatedAtBefore(DraftStatus.DRAFT, cutoff);
        Set<String> held = profiles.idsHeldForErasure();
        for (FilingDraft draft : untouched) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return;
            }
            if (!isPrimary(draft) && !held.contains(draft.getProfileId())) {
                draft.moveTo(DraftStatus.ARCHIVED, context.nowUtc());
                drafts.save(draft);
                log.info("Job {} archived draft {}", name(), draft.getId());
            }
        }
    }

    private boolean isPrimary(FilingDraft draft) {
        return workspaces.findById(draft.getWorkspaceId()).map(TaxYearWorkspace::getPrimaryDraftId)
                .filter(draft.getId()::equals).isPresent();
    }
}
