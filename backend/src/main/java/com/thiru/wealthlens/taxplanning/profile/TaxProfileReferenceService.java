package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people and accounts a profile refers to, referenced by id across years ({@code DR 15} F3, F4). Each write first proves the
 * profile is this account's.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaxProfileReferenceService {

    private final TaxProfileService taxProfileService;
    private final FamilyMemberRepository familyMemberRepository;
    private final EmployerRepository employerRepository;
    private final LandlordRepository landlordRepository;
    private final LenderRepository lenderRepository;
    private final BankAccountRepository bankAccountRepository;

    public FamilyMember createFamilyMember(String email, String profileId, FamilyMemberRequest request) {
        return create(email, profileId, request, new FamilyMember(), familyMemberRepository);
    }

    public FamilyMember replaceFamilyMember(String email, String profileId, String id, FamilyMemberRequest request) {
        return replace(email, profileId, id, request, familyMemberRepository);
    }

    @Transactional(readOnly = true)
    public List<FamilyMember> familyMembers(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        return familyMemberRepository.findByProfileId(profileId);
    }

    public Employer createEmployer(String email, String profileId, EmployerRequest request) {
        return create(email, profileId, request, new Employer(), employerRepository);
    }

    public Employer replaceEmployer(String email, String profileId, String id, EmployerRequest request) {
        return replace(email, profileId, id, request, employerRepository);
    }

    @Transactional(readOnly = true)
    public List<Employer> employers(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        return employerRepository.findByProfileId(profileId);
    }

    public Landlord createLandlord(String email, String profileId, LandlordRequest request) {
        return create(email, profileId, request, new Landlord(), landlordRepository);
    }

    public Landlord replaceLandlord(String email, String profileId, String id, LandlordRequest request) {
        return replace(email, profileId, id, request, landlordRepository);
    }

    @Transactional(readOnly = true)
    public List<Landlord> landlords(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        return landlordRepository.findByProfileId(profileId);
    }

    public Lender createLender(String email, String profileId, LenderRequest request) {
        return create(email, profileId, request, new Lender(), lenderRepository);
    }

    public Lender replaceLender(String email, String profileId, String id, LenderRequest request) {
        return replace(email, profileId, id, request, lenderRepository);
    }

    @Transactional(readOnly = true)
    public List<Lender> lenders(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        return lenderRepository.findByProfileId(profileId);
    }

    public BankAccount createBankAccount(String email, String profileId, BankAccountRequest request) {
        return create(email, profileId, request, new BankAccount(), bankAccountRepository);
    }

    public BankAccount replaceBankAccount(String email, String profileId, String id, BankAccountRequest request) {
        return replace(email, profileId, id, request, bankAccountRepository);
    }

    @Transactional(readOnly = true)
    public List<BankAccount> bankAccounts(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        return bankAccountRepository.findByProfileId(profileId);
    }

    private <T extends ProfileScoped> T create(String email, String profileId, ProfileReferenceRequest<T> request, T blank,
            MongoRepository<T, String> repository) {
        taxProfileService.owned(email, profileId);
        T record = request.applyTo(blank);
        record.setId(UUID.randomUUID().toString());
        record.setProfileId(profileId);
        return save(record, repository);
    }

    private <T extends ProfileScoped> T replace(String email, String profileId, String id, ProfileReferenceRequest<T> request,
            MongoRepository<T, String> repository) {
        taxProfileService.owned(email, profileId);
        T stored = repository.findById(id)
                .filter(record -> profileId.equals(record.getProfileId()))
                .orElseThrow(() -> new BadRequestException("This profile holds no such record"));
        return save(request.applyTo(stored), repository);
    }

    private static <T> T save(T record, MongoRepository<T, String> repository) {
        try {
            return repository.save(record);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("This profile already holds an employer with that TAN");
        }
    }
}
