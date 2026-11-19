package com.thiru.wealthlens.taxplanning.workspace;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxYearWorkspaceRepository extends MongoRepository<TaxYearWorkspace, String> {

    Optional<TaxYearWorkspace> findByProfileIdAndTaxYearStart(String profileId, int taxYearStart);
}
