package com.thiru.wealthlens.taxplanning.workspace;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxYearWorkspaceRepository extends JpaRepository<TaxYearWorkspace, String> {

    Optional<TaxYearWorkspace> findByProfileIdAndTaxYearStart(String profileId, int taxYearStart);

    void deleteByProfileId(String profileId);
}
