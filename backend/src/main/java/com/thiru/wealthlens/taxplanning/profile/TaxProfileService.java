package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxplanning.protection.IdentifierDigest;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Taxpayer profiles, each read and written only through the account that owns it. Logs carry ids, never a PAN ({@code DR 14} O1.1). */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class TaxProfileService {

    private final TaxpayerProfileRepository taxpayerProfileRepository;
    private final IdentifierDigest identifierDigest;
    private final TaxAccessLog taxAccessLog;

    public TaxpayerProfile create(String email, TaxpayerProfileRequest request) {
        TaxpayerProfile profile = request.applyTo(new TaxpayerProfile());
        profile.setId(UUID.randomUUID().toString());
        profile.setEmail(email);
        return save(profile);
    }

    /** Every profile of the account, each read recorded: a profile carries disability ({@code DR 14} O1.1). */
    public List<TaxpayerProfile> all(String email) {
        List<TaxpayerProfile> profiles = taxpayerProfileRepository.findByEmail(email);
        profiles.forEach(profile -> taxAccessLog.record(email, ReadSubject.PROFILE, profile.getId()));
        return profiles;
    }

    /** {@link #owned}, for showing to the user, so the read is recorded ({@code DR 14} O1.1). */
    public TaxpayerProfile read(String email, String profileId) {
        TaxpayerProfile profile = owned(email, profileId);
        taxAccessLog.record(email, ReadSubject.PROFILE, profileId);
        return profile;
    }

    /**
     * The profile, if this account owns it.
     *
     * @throws BadRequestException otherwise, saying the same whether it is absent or another account's
     */
    @Transactional(readOnly = true)
    public TaxpayerProfile owned(String email, String profileId) {
        return taxpayerProfileRepository.findById(profileId)
                .filter(profile -> email.equals(profile.getEmail()))
                .orElseThrow(() -> new BadRequestException("This account holds no such profile"));
    }

    public TaxpayerProfile replace(String email, String profileId, TaxpayerProfileRequest request) {
        return save(request.applyTo(owned(email, profileId)));
    }

    private TaxpayerProfile save(TaxpayerProfile profile) {
        if (profile.isHoldsPortfolio() && taxpayerProfileRepository.findByEmailAndHoldsPortfolioTrue(profile.getEmail()).stream()
                .anyMatch(holder -> !holder.getId().equals(profile.getId()))) {
            throw new BadRequestException("Another profile of this account already holds its portfolio");
        }
        profile.setPanDigest(identifierDigest.of(profile.getPan()));
        try {
            TaxpayerProfile saved = taxpayerProfileRepository.save(profile);
            log.info("Saved taxpayer profile {}", saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("A profile already holds the PAN " + new Pan(profile.getPan()).masked());
        }
    }
}
