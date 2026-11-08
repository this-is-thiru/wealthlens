package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Deletes landlords and lenders — data about people who are not the user — once {@link RetentionPeriods#thirdPartyAfter()} has
 * passed since the end of the last tax year the record can support ({@code DR 14} O5; W-239, C1a). No draft names the record it
 * relied on ({@code DR 15} F3's reference is W-239a), so that year is the later of the profile's latest stored draft and the year
 * the record was last entered or changed: any of the profile's drafts may be the return it supports.
 */
@Component
@Log4j2
@RequiredArgsConstructor
class ThirdPartyRetention {

    private final LandlordRepository landlords;
    private final LenderRepository lenders;
    private final FilingDraftRepository drafts;
    private final RetentionPeriods periods;

    /** Stops at the first record it would delete once the lease is lost. */
    void purge(String job, JobContext context) {
        LocalDate today = context.nowUtc().toLocalDate();
        Map<String, OptionalInt> latestDraftYears = new HashMap<>();
        for (HeldRecord held : held()) {
            Optional<TaxYearId> changedIn = held.lastChangedIn();
            if (changedIn.isEmpty()) {
                context.refuse(held.subject(), "it has no stored date of entry or change, so how long it may be kept cannot be "
                        + "measured; kept");
                continue;
            }
            OptionalInt latestDraftYear = latestDraftYears.computeIfAbsent(held.profileId(), this::latestDraftYear);
            int lastSupported = Math.max(changedIn.get().startYear(), latestDraftYear.orElse(Integer.MIN_VALUE));
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
                landlords.findAll().stream().map(landlord -> new HeldRecord("landlord", landlord.getId(), landlord.getProfileId(),
                        landlord.getAuditMetadata(), landlords::deleteById)),
                lenders.findAll().stream().map(lender -> new HeldRecord("lender", lender.getId(), lender.getProfileId(),
                        lender.getAuditMetadata(), lenders::deleteById)))
                .toList();
    }

    private OptionalInt latestDraftYear(String profileId) {
        return drafts.findByProfileId(profileId).stream().mapToInt(FilingDraft::getTaxYearStart).max();
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
