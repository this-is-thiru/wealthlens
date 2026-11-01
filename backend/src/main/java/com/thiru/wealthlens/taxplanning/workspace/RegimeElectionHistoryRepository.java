package com.thiru.wealthlens.taxplanning.workspace;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegimeElectionHistoryRepository extends MongoRepository<RegimeElectionHistory, String> {

    Optional<RegimeElectionHistory> findByProfileIdAndRegimeCode(String profileId, String regimeCode);
}
