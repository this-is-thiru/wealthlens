package com.thiru.wealthlens.taxplanning.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {

    /** Everything of this kind a profile holds. */
    List<BankAccount> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
