package com.thiru.wealthlens.taxplanning.erasure;

import com.thiru.wealthlens.shared.privacy.PrivacySettings;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasure;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasureRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's request to erase a profile (Digital Personal Data Protection Act, 2023 s.12(3); W-235). The request stops all processing at
 * once; the retention purge erases the profile once the hold has passed. The request is also recorded apart from the profile, so
 * that a restore from an older snapshot cannot undo it (W-237f).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ErasureService {

    private final TaxProfileService taxProfileService;
    private final ProfileErasureRepository erasures;
    private final PrivacySettings privacy;
    private final Clock clock;

    public ErasureReceipt request(String email, String profileId) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        TaxpayerProfile held = taxProfileService.holdForErasure(email, profileId, now);
        ProfileErasure erasure = new ProfileErasure();
        erasure.setEmail(email);
        erasure.setProfileId(held.getId());
        erasure.setRequestedAt(now);
        erasures.save(erasure);
        return new ErasureReceipt(held.getId(), now, now.plus(privacy.erasureHold()), ErasureReceipt.ERASED, ErasureReceipt.HELD,
                privacy.contact());
    }
}
