package com.thiru.wealthlens.taxplanning.policy.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.dto.ResolvedAllowance;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceCatalogueRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceLimitRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class AllowanceResolutionService {

    private final AllowanceLimitRepository limitRepo;
    private final AllowanceCatalogueRepository catalogueRepo;

    public ResolvedAllowance resolve(String allowanceCode, String taxYear,
                                      RegimeType regime, EmployerType employer) {
        AllowanceCatalogueEntity catalogue = null;
        try {
            catalogue = catalogueRepo.findByCodeAndStatus(allowanceCode, EntityStatus.ACTIVE)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not look up catalogue for {}: {}", allowanceCode, e.getMessage());
        }

        AllowanceLimitEntity limit = limitRepo
                .findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerTypeAndStatusOrderByEffectiveDateDesc(
                        allowanceCode, taxYear, regime, employer, EntityStatus.ACTIVE)
                .or(() -> limitRepo.findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndStatusOrderByEffectiveDateDesc(
                        allowanceCode, taxYear, regime, EntityStatus.ACTIVE))
                .or(() -> limitRepo.findTopByAllowanceCodeAndTaxYearAndStatusOrderByEffectiveDateDesc(
                        allowanceCode, taxYear, EntityStatus.ACTIVE))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active limit for " + allowanceCode + " regime=" + regime + " employer=" + employer + " year=" + taxYear));

        return ResolvedAllowance.builder().metadata(catalogue).limit(limit).build();
    }

    public List<AllowanceLimitEntity> findAllActiveForYear(String taxYear) {
        return limitRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE);
    }

    public Set<String> findAvailableAllowanceCodes(String taxYear, RegimeType regime) {
        return limitRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE).stream()
                .filter(l -> l.getRegimeType() == regime)
                .map(AllowanceLimitEntity::getAllowanceCode)
                .collect(Collectors.toSet());
    }
}
