package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasureRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Deletes drafts whose retention has run out ({@code DR 14} O5; W-136, D-449). A draft ever acted on is kept to the end of the
 * last tax year its own year's law lets a loss be carried into, read from the rule pack; a year whose pack says nothing is refused
 * and kept. Any other archived draft is kept for {@link RetentionPeriods#purgeArchivedAfter()} after it was archived. A profile
 * held for erasure is erased whole once {@link PrivacySettings#erasureHold()} has passed since the request (W-235); its erasure
 * record is completed first, so a run cut off in between leaves the profile held, and the next run erases it (W-237f).
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class RetentionPurge implements TaxJob {

    private final FilingDraftRepository drafts;
    private final DraftRemoval removal;
    private final TaxCalculator taxCalculator;
    private final RetentionPeriods periods;
    private final TaxpayerProfileRepository profiles;
    private final ProfileRemoval profileRemoval;
    private final ProfileErasureRepository profileErasures;
    private final PrivacySettings privacy;

    @Override
    public String name() {
        return "retention-purge";
    }

    @Override
    public Duration timeout() {
        return Duration.ofHours(1);
    }

    @Override
    public void run(JobContext context) {
        if (!eraseHeldProfiles(context)) {
            return;
        }
        List<FilingDraft> archived = drafts.findByStatusAndActedOnAtIsNullAndArchivedAtBefore(DraftStatus.ARCHIVED,
                context.nowUtc().minus(periods.purgeArchivedAfter()));
        if (!removeAll(archived, context)) {
            return;
        }
        Map<Integer, List<FilingDraft>> actedOnByYear = drafts.findByActedOnAtIsNotNull().stream()
                .collect(Collectors.groupingBy(FilingDraft::getTaxYearStart));
        LocalDate today = context.nowUtc().toLocalDate();
        for (Map.Entry<Integer, List<FilingDraft>> year : actedOnByYear.entrySet()) {
            TaxYearId taxYear = TaxYearId.of(year.getKey());
            OptionalInt span = taxCalculator.longestCarryForward(taxYear);
            if (span.isEmpty()) {
                context.refuse("tax year " + taxYear.label(), "no shipped pack carries a loss forward for it, so how long its "
                        + year.getValue().size() + " filed draft(s) must be kept cannot be derived; kept");
            } else if (today.isAfter(TaxYearId.of(taxYear.startYear() + span.getAsInt()).endsOn())
                    && !removeAll(year.getValue(), context)) {
                return;
            }
        }
    }

    private boolean eraseHeldProfiles(JobContext context) {
        for (TaxpayerProfile profile : profiles.findByErasureRequestedAtLessThanEqual(
                context.nowUtc().minus(privacy.erasureHold()))) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return false;
            }
            profileErasures.findByProfileIdAndErasedAtIsNull(profile.getId()).ifPresent(erasure -> {
                erasure.setErasedAt(context.nowUtc());
                profileErasures.save(erasure);
            });
            profileRemoval.remove(profile);
            log.info("Job {} erased profile {}", name(), profile.getId());
        }
        return true;
    }

    private boolean removeAll(List<FilingDraft> expired, JobContext context) {
        for (FilingDraft draft : expired) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return false;
            }
            removal.remove(draft);
            log.info("Job {} deleted draft {} of tax year {}", name(), draft.getId(), draft.getTaxYearStart());
        }
        return true;
    }
}
