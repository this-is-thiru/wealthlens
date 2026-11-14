package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BankAccountRepository extends MongoRepository<BankAccount, String> {

    /** Everything of this kind a profile holds. */
    List<BankAccount> findByProfileId(String profileId);
}
