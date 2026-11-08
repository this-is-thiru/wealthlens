package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.protection.IdentifierDigest;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final IdentifierDigest identifierDigest;
    private final TaxAccessLog taxAccessLog;

    public FamilyMember createFamilyMember(String email, String profileId, FamilyMemberRequest request) {
        return create(email, profileId, request, new FamilyMember(), familyMemberRepository);
    }

    public FamilyMember replaceFamilyMember(String email, String profileId, String id, FamilyMemberRequest request) {
        return replace(email, profileId, id, request, familyMemberRepository);
    }

    /** The profile's family, its read recorded: family composition and disability are special-category data ({@code DR 14} O1.1). */
    public List<FamilyMember> familyMembers(String email, String profileId) {
        taxProfileService.owned(email, profileId);
        taxAccessLog.record(email, ReadSubject.FAMILY_MEMBERS, profileId);
        return familyMemberRepository.findByProfileId(profileId);
    }

    public Employer createEmployer(String email, String profileId, EmployerRequest request) {
        return create(email, profileId, withTanDigest(request), new Employer(), employerRepository);
    }

    public Employer replaceEmployer(String email, String profileId, String id, EmployerRequest request) {
        return replace(email, profileId, id, withTanDigest(request), employerRepository);
    }

    private ProfileReferenceRequest<Employer> withTanDigest(EmployerRequest request) {
        return record -> {
            Employer employer = request.applyTo(record);
            employer.setTanDigest(identifierDigest.of(employer.getTan()));
            return employer;
        };
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

    /**
     * Refuses unless every id is one of this profile's landlords or lenders: a draft names only what its own profile holds
     * (W-239a C2a).
     *
     * @throws BadRequestException naming the first id the profile does not hold
     */
    @Transactional(readOnly = true)
    public void requireHeld(String profileId, Collection<String> landlordIds, Collection<String> lenderIds) {
        requireAmong(landlordIds, landlordRepository.findByProfileId(profileId).stream().map(Landlord::getId), "landlord");
        requireAmong(lenderIds, lenderRepository.findByProfileId(profileId).stream().map(Lender::getId), "lender");
    }

    private static void requireAmong(Collection<String> named, Stream<String> held, String kind) {
        if (named.isEmpty()) {
            return;
        }
        Set<String> heldIds = held.collect(Collectors.toSet());
        named.stream().filter(id -> !heldIds.contains(id)).findFirst().ifPresent(id -> {
            throw new BadRequestException(kind + " " + id + " is not one of this profile's " + kind + "s");
        });
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
