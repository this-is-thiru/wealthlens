package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentRemoval;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.profile.BankAccountRepository;
import com.thiru.wealthlens.taxplanning.profile.EmployerRepository;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberRepository;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistoryRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases a profile and everything stored under it, in one transaction (W-235 C1a, C4a): its drafts with their revisions and
 * computations, the people and accounts it refers to (third parties' data included), its workspaces, its regime election
 * history, the access records naming it, one of its drafts or one of its receipts, and its documents with what was read from
 * them (W-131a). Nothing is kept: no law binds WealthLens to
 * keep a filed computation ({@code sources/2026-11-07-data-protection.md}).
 */
@Service
@Transactional("postgresTransactionManager")
@RequiredArgsConstructor
public class ProfileRemoval {

    private final TaxpayerProfileRepository profiles;
    private final FilingDraftRepository drafts;
    private final DraftComputationRepository computations;
    private final DraftRemoval draftRemoval;
    private final AccessRecordRepository accessRecords;
    private final DocumentRemoval documentRemoval;
    private final StoredDocumentRepository documents;
    private final FamilyMemberRepository familyMembers;
    private final EmployerRepository employers;
    private final LandlordRepository landlords;
    private final LenderRepository lenders;
    private final BankAccountRepository bankAccounts;
    private final TaxYearWorkspaceRepository workspaces;
    private final RegimeElectionHistoryRepository regimeElectionHistories;

    public void remove(TaxpayerProfile profile) {
        Set<String> readSubjects = new HashSet<>();
        readSubjects.add(profile.getId());
        for (FilingDraft draft : drafts.findByProfileId(profile.getId())) {
            readSubjects.add(draft.getId());
            computations.findByDraftId(draft.getId()).stream().map(DraftComputation::getReceipt).filter(Objects::nonNull)
                    .forEach(readSubjects::add);
            draftRemoval.remove(draft);
        }
        documents.findByProfileId(profile.getId()).forEach(document -> readSubjects.add(document.getId()));
        accessRecords.deleteBySubjectIdIn(readSubjects);
        documentRemoval.removeUnder(profile.getId());
        familyMembers.deleteByProfileId(profile.getId());
        employers.deleteByProfileId(profile.getId());
        landlords.deleteByProfileId(profile.getId());
        lenders.deleteByProfileId(profile.getId());
        bankAccounts.deleteByProfileId(profile.getId());
        workspaces.deleteByProfileId(profile.getId());
        regimeElectionHistories.deleteByProfileId(profile.getId());
        profiles.delete(profile);
    }
}
