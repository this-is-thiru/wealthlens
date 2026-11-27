package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeElectionHistoryRepository extends JpaRepository<RegimeElectionHistory, String> {

    Optional<RegimeElectionHistory> findByProfileIdAndRegimeCode(String profileId, String regimeCode);

    List<RegimeElectionHistory> findByProfileId(String profileId);

    void deleteByProfileId(String profileId);
}
