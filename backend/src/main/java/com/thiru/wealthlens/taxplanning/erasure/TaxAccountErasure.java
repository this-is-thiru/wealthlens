package com.thiru.wealthlens.taxplanning.erasure;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.jobs.ProfileRemoval;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * A person's tax data, held and erased with their account (W-237d). Each profile is held as W-235 holds one, so the tax jobs
 * leave it alone and it reads as absent; at erasure every profile goes, and so does what is kept by email rather than under a
 * profile: the older flow's salary profiles and computations, any draft computation left without its draft, and the record of
 * each read the person made.
 */
@Component
@RequiredArgsConstructor
public class TaxAccountErasure implements PersonalDataHolder {

    private final TaxpayerProfileRepository profiles;
    private final TaxProfileService taxProfileService;
    private final ProfileRemoval profileRemoval;
    private final MongoTemplate mongoTemplate;

    @Override
    public void hold(String email, LocalDateTime requestedAt) {
        profiles.findByEmail(email).stream().filter(profile -> profile.getErasureRequestedAt() == null)
                .forEach(profile -> taxProfileService.holdForErasure(email, profile.getId(), requestedAt));
    }

    @Override
    public void erase(String email) {
        for (TaxpayerProfile profile : profiles.findByEmail(email)) {
            profileRemoval.remove(profile);
        }
        mongoTemplate.remove(query(where("email").is(email)), SalaryProfileEntity.class);
        mongoTemplate.remove(query(where("email").is(email)), TaxComputationEntity.class);
        mongoTemplate.remove(query(where("email").is(email)), DraftComputation.class);
        mongoTemplate.remove(query(where("principal").is(email)), AccessRecord.class);
    }
}
