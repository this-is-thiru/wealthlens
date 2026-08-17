package com.thiru.wealthlens.taxplanning.policy.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceCatalogueRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.PerquisitePolicyRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.TaxSlabPolicyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final TaxSlabPolicyRepository slabRepo;
    private final PerquisitePolicyRepository perquisiteRepo;
    private final AllowanceCatalogueRepository allowanceRepo;

    public TaxSlabPolicyEntity getSlabPolicy(String taxYear, RegimeType regime) {
        return slabRepo.findByTaxYearAndRegimeTypeAndStatus(taxYear, regime, EntityStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active tax slab policy found for year=" + taxYear + " regime=" + regime));
    }

    public PerquisitePolicyEntity getPerquisitePolicy(String taxYear) {
        return perquisiteRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active perquisite policy found for year=" + taxYear));
    }

    public List<AllowanceCatalogueEntity> getAllowanceCatalogue(String taxYear) {
        return allowanceRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE);
    }

    public List<AllowanceCatalogueEntity> getAllowanceCatalogue(String taxYear, RegimeType regime) {
        return allowanceRepo.findByTaxYearAndRegimeTypeAndStatus(taxYear, regime, EntityStatus.ACTIVE);
    }

    @Transactional
    public TaxSlabPolicyEntity createSlabPolicy(TaxSlabPolicyEntity policy) {
        List<TaxSlabPolicyEntity> existing = slabRepo.findByTaxYearAndRegimeType(policy.getTaxYear(), policy.getRegimeType());
        existing.stream().filter(p -> p.getStatus() == EntityStatus.ACTIVE).forEach(p -> {
            p.setStatus(EntityStatus.SUPERSEDED);
            slabRepo.save(p);
        });
        policy.setStatus(EntityStatus.ACTIVE);
        return slabRepo.save(policy);
    }

    @Transactional
    public PerquisitePolicyEntity createPerquisitePolicy(PerquisitePolicyEntity policy) {
        perquisiteRepo.findByTaxYearAndStatus(policy.getTaxYear(), EntityStatus.ACTIVE).ifPresent(p -> {
            p.setStatus(EntityStatus.SUPERSEDED);
            perquisiteRepo.save(p);
        });
        policy.setStatus(EntityStatus.ACTIVE);
        return perquisiteRepo.save(policy);
    }

    @Transactional
    public AllowanceCatalogueEntity updateAllowance(String code, String taxYear, AllowanceCatalogueEntity allowance) {
        AllowanceCatalogueEntity existing = allowanceRepo.findByCodeAndTaxYearAndRegimeType(code, taxYear, allowance.getRegimeType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Allowance not found: " + code + " regime=" + allowance.getRegimeType()));
        existing.setAnnualLimitFixed(allowance.getAnnualLimitFixed());
        existing.setMonthlyLimitFixed(allowance.getMonthlyLimitFixed());
        existing.setDescription(allowance.getDescription());
        existing.setStatus(allowance.getStatus());
        return allowanceRepo.save(existing);
    }

    public List<TaxSlabPolicyEntity> getSlabPolicies(String taxYear) {
        return slabRepo.findByTaxYear(taxYear);
    }
}
