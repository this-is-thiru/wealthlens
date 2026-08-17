package com.thiru.wealthlens.brokercharges.repository;

import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BrokerChargesRepository extends MongoRepository<BrokerCharges, String> {

    @Query("{'broker_name': ?0, 'status': 'ACTIVE', 'start_date': { $lte: ?1 }, 'end_date': { $gte: ?1 } }")
    Optional<BrokerCharges> findActiveBrokerChargesOnDate(BrokerName brokerName, LocalDate targetDate);

}
