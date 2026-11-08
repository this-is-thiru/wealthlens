package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftInputs;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Deletes landlords and lenders — data about people who are not the user — once {@link RetentionPeriods#thirdPartyAfter()} has
 * passed since the end of the last tax year the record can support ({@code DR 14} O5; W-239, D-505). That year is the later of
 * the latest tax year of a stored draft naming the record and the year it was last entered or changed; a record no draft names
 * supports only the year of its last change (W-239a C3a, D-506).
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ThirdPartyRetention {

    private static final String LANDLORD = "landlord";
    private static final String LENDER = "lender";

    private final LandlordRepository landlords;
    private final LenderRepository lenders;
    private final FilingDraftRepository drafts;
    private final RetentionPeriods periods;

    /** Stops at the first record it would delete once the lease is lost. */
    void purge(String job, JobContext context) {
        LocalDate today = context.nowUtc().toLocalDate();
        Map<String, Map<String, Integer>> namingYearsByProfile = new HashMap<>();
        for (HeldRecord held : held()) {
            Optional<TaxYearId> changedIn = held.lastChangedIn();
            if (changedIn.isEmpty()) {
                context.refuse(held.subject(), "it has no stored date of entry or change, so how long it may be kept cannot be "
                        + "measured; kept");
                continue;
            }
            Integer namingYear = namingYearsByProfile.computeIfAbsent(held.profileId(), this::latestNamingYears).get(held.subject());
            int lastSupported = namingYear == null ? changedIn.get().startYear()
                    : Math.max(changedIn.get().startYear(), namingYear);
            if (!today.isAfter(TaxYearId.of(lastSupported).endsOn().plus(periods.thirdPartyAfter()))) {
                continue;
            }
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", job);
                return;
            }
            held.remover().accept(held.id());
            log.info("Job {} deleted {}", job, held.subject());
        }
    }

    private List<HeldRecord> held() {
        return Stream.concat(
                landlords.findAll().stream().map(landlord -> new HeldRecord(LANDLORD, landlord.getId(), landlord.getProfileId(),
                        landlord.getAuditMetadata(), landlords::deleteById)),
                lenders.findAll().stream().map(lender -> new HeldRecord(LENDER, lender.getId(), lender.getProfileId(),
                        lender.getAuditMetadata(), lenders::deleteById)))
                .toList();
    }

    /** For each record the profile's drafts name, keyed as {@link HeldRecord#subject()}, the latest tax year naming it. */
    private Map<String, Integer> latestNamingYears(String profileId) {
        Map<String, Integer> years = new HashMap<>();
        for (FilingDraft draft : drafts.findByProfileId(profileId)) {
            DraftInputs.ThirdParties named = DraftInputs.of(draft).thirdParties();
            Stream.concat(named.landlordIds().stream().map(id -> LANDLORD + " " + id),
                    named.lenderIds().stream().map(id -> LENDER + " " + id))
                    .forEach(subject -> years.merge(subject, draft.getTaxYearStart(), Math::max));
        }
        return years;
    }

    /** A landlord or lender as retention sees it: whose it is, when it last changed, and how to delete it. */
    private record HeldRecord(String kind, String id, String profileId, AuditMetadata audit, Consumer<String> remover) {

        String subject() {
            return kind + " " + id;
        }

        Optional<TaxYearId> lastChangedIn() {
            return Optional.ofNullable(audit).flatMap(stored -> Optional.ofNullable(stored.getUpdatedAt())
                    .or(() -> Optional.ofNullable(stored.getCreatedAt())))
                    .map(LocalDateTime::toLocalDate).map(TaxYearId::containing);
        }
    }
}
