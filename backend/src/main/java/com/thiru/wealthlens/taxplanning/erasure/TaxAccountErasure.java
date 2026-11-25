package com.thiru.wealthlens.taxplanning.erasure;

import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.jobs.ProfileRemoval;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasure;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasureRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A person's tax data, held and erased with their account (W-237d). Each profile is held as W-235 holds one, so the tax jobs
 * leave it alone and it reads as absent; at erasure every profile goes, and so does what is kept by email rather than under a
 * profile: any draft computation left without its draft, the record of each read the person made, and the record of each profile they asked to erase. After a restore, it applies again each profile
 * erasure and hold the restored snapshot predates (W-237f C2a′).
 */
@Component
@RequiredArgsConstructor
public class TaxAccountErasure implements PersonalDataHolder {

    private final TaxpayerProfileRepository profiles;
    private final TaxProfileService taxProfileService;
    private final ProfileRemoval profileRemoval;
    private final ProfileErasureRepository profileErasures;
    private final DraftComputationRepository computations;
    private final AccessRecordRepository accessRecords;

    @Override
    public void hold(String email, LocalDateTime requestedAt) {
        profiles.findByEmail(email).stream().filter(profile -> profile.getErasureRequestedAt() == null)
                .forEach(profile -> taxProfileService.holdForErasure(email, profile.getId(), requestedAt));
    }

    @Override
    public String noticePart() {
        return "tax-planning";
    }

    @Override
    public void erase(String email) {
        for (TaxpayerProfile profile : profiles.findByEmail(email)) {
            profileRemoval.remove(profile);
        }
        computations.deleteByEmail(email);
        accessRecords.deleteByPrincipal(email);
        profileErasures.deleteByEmail(email);
    }

    /** Profile ids are never reused, so erasing a restored profile by its id cannot reach a profile made since. */
    @Override
    public List<String> reapply(LocalDateTime snapshotTakenAt) {
        List<String> reapplied = new ArrayList<>();
        for (ProfileErasure erased : profileErasures.findByErasedAtGreaterThanEqual(snapshotTakenAt)) {
            profiles.findById(erased.getProfileId()).ifPresent(profile -> {
                profileRemoval.remove(profile);
                reapplied.add(profile.getId());
            });
        }
        for (ProfileErasure asked : profileErasures.findByErasedAtIsNullAndRequestedAtGreaterThanEqual(snapshotTakenAt)) {
            profiles.findById(asked.getProfileId()).filter(profile -> profile.getErasureRequestedAt() == null)
                    .ifPresent(profile -> {
                        taxProfileService.holdForErasure(asked.getEmail(), profile.getId(), asked.getRequestedAt());
                        reapplied.add(profile.getId());
                    });
        }
        return reapplied;
    }
}
