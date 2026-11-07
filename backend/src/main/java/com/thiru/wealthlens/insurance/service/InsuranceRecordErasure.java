package com.thiru.wealthlens.insurance.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.insurance.entity.InsuranceEntity;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * A person's insurance policies, erased with their account (W-237d), the agent's details in them included. Nothing writes
 * policies today, but the collection exists and may hold rows from earlier versions.
 */
@Component
@RequiredArgsConstructor
public class InsuranceRecordErasure implements PersonalDataHolder {

    private final MongoTemplate mongoTemplate;

    @Override
    public void erase(String email) {
        mongoTemplate.remove(query(where("email").is(email)), InsuranceEntity.class);
    }
}
