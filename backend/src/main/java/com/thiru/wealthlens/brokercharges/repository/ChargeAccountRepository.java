package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ChargeAccountRepository extends MongoRepository<ChargeAccountEntity, String> {

    Optional<ChargeAccountEntity> findByEmailAndBrokerNameAndDematAccountId(
            String email, BrokerName brokerName, String dematAccountId);

    List<ChargeAccountEntity> findByEmail(String email);

    /**
     * Accounts due an annual-maintenance charge: those on the given frequency whose last billed
     * date falls before the cut-off. An account already billed through that date is not returned,
     * which is what makes re-running a cycle a no-op rather than a second charge.
     */
    @Query("{ 'amc_frequency': ?0, 'status': 'ACTIVE',"
            + " $or: [ { 'last_billed_through': null }, { 'last_billed_through': { $lt: ?1 } } ] }")
    List<ChargeAccountEntity> findDueForAmc(AmcChargeFrequency frequency, LocalDate billedThroughBefore);

    void deleteByEmail(String email);
}
