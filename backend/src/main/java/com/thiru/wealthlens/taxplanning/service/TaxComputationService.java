package com.thiru.wealthlens.taxplanning.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.engine.FormulaEvaluator;
import com.thiru.wealthlens.taxplanning.engine.TaxEngine;
import com.thiru.wealthlens.taxplanning.engine.TaxEngineFactory;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.PolicyService;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import com.thiru.wealthlens.taxplanning.salary.repository.SalaryProfileRepository;
import com.thiru.wealthlens.taxplanning.salary.repository.TaxComputationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxComputationService {

    private final SalaryProfileRepository profileRepo;
    private final TaxComputationRepository computationRepo;
    private final PolicyService policyService;
    private final TaxEngineFactory engineFactory;
    private final FormulaEvaluator formulaEvaluator;

    @Transactional
    public TaxComputationEntity compute(String email, String profileId) {
        // 1. Load profile
        SalaryProfileEntity profile = profileRepo.findById(profileId)
                .filter(p -> p.getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Salary profile not found with id=" + profileId + " for email=" + email));

        String taxYear = profile.getTaxYear();

        // 2. Load slab policies
        TaxSlabPolicyEntity newSlab = policyService.getSlabPolicy(taxYear, RegimeType.NEW_REGIME);
        TaxSlabPolicyEntity oldSlab = policyService.getSlabPolicy(taxYear, RegimeType.OLD_REGIME);

        // 3. Load perquisite policy
        PerquisitePolicyEntity perquisite = policyService.getPerquisitePolicy(taxYear);

        // 4. Load allowance catalogue
        List<AllowanceCatalogueEntity> catalogue = policyService.getAllowanceCatalogue(taxYear);

        // 5. Run new regime engine
        TaxEngine newRegimeEngine = engineFactory.getNewRegimeEngine();
        TaxComputationEntity.TaxResult newResult = newRegimeEngine.compute(
                profile, newSlab, perquisite, catalogue, formulaEvaluator);

        // 6. Run old regime engine
        TaxEngine oldRegimeEngine = engineFactory.getOldRegimeEngine();
        TaxComputationEntity.TaxResult oldResult = oldRegimeEngine.compute(
                profile, oldSlab, perquisite, catalogue, formulaEvaluator);

        // 7. Determine recommended regime
        RegimeType recommendedRegime = newResult.getTotalTax() <= oldResult.getTotalTax()
                ? RegimeType.NEW_REGIME : RegimeType.OLD_REGIME;

        // 8. Compute saving vs current regime
        long annualSaving = Math.abs(oldResult.getTotalTax() - newResult.getTotalTax());

        // 9. Save to tax_computations (upsert by salaryProfileId)
        TaxComputationEntity computation = computationRepo.findBySalaryProfileId(profileId)
                .stream().findFirst()
                .orElseGet(TaxComputationEntity::new);

        computation.setSalaryProfileId(profileId);
        computation.setEmail(email);
        computation.setTaxYear(taxYear);
        computation.setNewRegimeResult(newResult);
        computation.setOldRegimeResult(oldResult);
        computation.setRecommendedRegime(recommendedRegime);
        computation.setAnnualSavingVsCurrent(annualSaving);
        computation.setStatus(EntityStatus.ACTIVE);

        TaxComputationEntity saved = computationRepo.save(computation);
        log.info("Computed tax for profile {}: new={} old={} recommended={}",
                profileId, newResult.getTotalTax(), oldResult.getTotalTax(), recommendedRegime);

        return saved;
    }

    @Transactional(readOnly = true)
    public TaxComputationEntity getComputation(String email, String profileId) {
        return computationRepo.findBySalaryProfileId(profileId)
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tax computation found for profile=" + profileId));
    }
}
