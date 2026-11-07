package com.thiru.wealthlens.taxplanning.erasure;

import com.thiru.wealthlens.taxplanning.jobs.RetentionPeriods;
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
 * once; the retention purge erases the profile once the hold has passed.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ErasureService {

    private final TaxProfileService taxProfileService;
    private final RetentionPeriods periods;
    private final PrivacyContact privacyContact;
    private final Clock clock;

    public ErasureReceipt request(String email, String profileId) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        TaxpayerProfile held = taxProfileService.holdForErasure(email, profileId, now);
        return new ErasureReceipt(held.getId(), now, now.plus(periods.erasureHold()), ErasureReceipt.ERASED, ErasureReceipt.HELD,
                privacyContact.privacyContact());
    }
}
