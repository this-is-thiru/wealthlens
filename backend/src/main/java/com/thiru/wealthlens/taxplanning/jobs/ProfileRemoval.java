package com.thiru.wealthlens.taxplanning.jobs;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.profile.BankAccount;
import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases a profile and everything stored under it, in one transaction (W-235 C1a, C4a): its drafts with their revisions and
 * computations, the people and accounts it refers to (third parties' data included), its workspaces, its regime election
 * history, and the access records naming it, one of its drafts or one of its receipts. Nothing is kept: no law binds WealthLens to
 * keep a filed computation ({@code sources/2026-11-07-data-protection.md}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProfileRemoval {

    /** Every collection that holds a record per profile under {@code profile_id}, apart from the drafts. */
    private static final List<Class<?>> PROFILE_SCOPED = List.of(FamilyMember.class, Employer.class, Landlord.class, Lender.class,
            BankAccount.class, TaxYearWorkspace.class, RegimeElectionHistory.class);

    private final TaxpayerProfileRepository profiles;
    private final FilingDraftRepository drafts;
    private final DraftComputationRepository computations;
    private final DraftRemoval draftRemoval;
    private final AccessRecordRepository accessRecords;
    private final MongoTemplate mongoTemplate;

    public void remove(TaxpayerProfile profile) {
        Set<String> readSubjects = new HashSet<>();
        readSubjects.add(profile.getId());
        for (FilingDraft draft : drafts.findByProfileId(profile.getId())) {
            readSubjects.add(draft.getId());
            computations.findByDraftId(draft.getId()).stream().map(DraftComputation::getReceipt).filter(Objects::nonNull)
                    .forEach(readSubjects::add);
            draftRemoval.remove(draft);
        }
        accessRecords.deleteBySubjectIdIn(readSubjects);
        PROFILE_SCOPED.forEach(scoped -> mongoTemplate.remove(query(where("profile_id").is(profile.getId())), scoped));
        profiles.delete(profile);
    }
}
