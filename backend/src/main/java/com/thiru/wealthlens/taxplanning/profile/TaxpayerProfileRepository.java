package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxpayerProfileRepository extends MongoRepository<TaxpayerProfile, String> {

    /** Every profile of an account. */
    List<TaxpayerProfile> findByEmail(String email);
}
