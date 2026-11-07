package com.thiru.wealthlens.shared.privacy;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Erases a person's grievances and breach intimations, and takes their email out of each breach notice (W-237d C7a). The
 * notice itself stays: it is the owner's record to the Board about the breach, not about the person.
 */
@Component
@RequiredArgsConstructor
public class PrivacyRecordErasure implements PersonalDataHolder {

    private final MongoTemplate mongoTemplate;

    @Override
    public void erase(String email) {
        mongoTemplate.remove(query(where("email").is(email)), Grievance.class);
        mongoTemplate.remove(query(where("email").is(email)), BreachIntimation.class);
        mongoTemplate.updateMulti(query(where("affected").is(email)), new Update().pull("affected", email), BreachNotice.class);
    }
}
