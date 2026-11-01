package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.exception.BadRequestException;
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

    public TaxpayerProfile create(String email, TaxpayerProfileRequest request) {
        TaxpayerProfile profile = request.applyTo(new TaxpayerProfile());
        profile.setId(UUID.randomUUID().toString());
        profile.setEmail(email);
        return save(profile);
    }

    @Transactional(readOnly = true)
    public List<TaxpayerProfile> all(String email) {
        return taxpayerProfileRepository.findByEmail(email);
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
        try {
            TaxpayerProfile saved = taxpayerProfileRepository.save(profile);
            log.info("Saved taxpayer profile {}", saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("A profile already holds the PAN " + new Pan(profile.getPan()).masked());
        }
    }
}
