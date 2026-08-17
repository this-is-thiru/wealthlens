package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.engine.FormulaEvaluator;
import com.thiru.wealthlens.taxplanning.engine.PerquisiteValuationService;
import com.thiru.wealthlens.taxplanning.engine.TaxEngineFactory;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.AllowanceResolutionService;
import com.thiru.wealthlens.taxplanning.policy.service.PolicyService;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class RestructuringEngine {

    private final PolicyService policyService;
    private final TaxEngineFactory engineFactory;
    private final FormulaEvaluator formulaEvaluator;
    private final PerquisiteValuationService perquisiteService;
    private final RegimeAdvisor regimeAdvisor;
    private final AllowanceResolutionService resolutionService;

    public RestructuringResult restructure(SalaryProfileEntity currentProfile) {
        String taxYear = currentProfile.getTaxYear();
        RegimeType currentRegime = currentProfile.getRegimeType() != null
                ? currentProfile.getRegimeType() : RegimeType.NEW_REGIME;

        // Load policies
        List<AllowanceCatalogueEntity> catalogue = policyService.getAllowanceCatalogue(taxYear);
        PerquisitePolicyEntity perquisitePolicy = policyService.getPerquisitePolicy(taxYear);
        TaxSlabPolicyEntity slabPolicy = policyService.getSlabPolicy(taxYear, RegimeType.NEW_REGIME);

        // Compute original tax for both regimes
        TaxComputationEntity.TaxResult originalNewResult = engineFactory.getEngine(RegimeType.NEW_REGIME)
                .compute(currentProfile,
                        policyService.getSlabPolicy(taxYear, RegimeType.NEW_REGIME),
                        perquisitePolicy, catalogue, formulaEvaluator);
        TaxComputationEntity.TaxResult originalOldResult = engineFactory.getEngine(RegimeType.OLD_REGIME)
                .compute(currentProfile,
                        policyService.getSlabPolicy(taxYear, RegimeType.OLD_REGIME),
                        perquisitePolicy, catalogue, formulaEvaluator);

        long originalNewRegimeTax = originalNewResult.getTotalTax();
        long originalOldRegimeTax = originalOldResult.getTotalTax();

        // 1. Identify reducible pool = Special Allowance
        long reduciblePool = getSpecialAllowance(currentProfile);

        // 2. Filter eligible allowances
        List<String> existingCodes = currentProfile.getComponents().stream()
                .map(SalaryComponentEntity::getAllowanceCode)
                .toList();

        Set<String> availableCodes = resolutionService.findAvailableAllowanceCodes(taxYear, currentRegime);

        List<AllowanceCatalogueEntity> eligible = catalogue.stream()
                .filter(a -> a.getRegimeType() == currentRegime)
                .filter(a -> availableCodes.contains(a.getCode()))
                .filter(a -> !existingCodes.contains(a.getCode()))
                .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
                .sorted(Comparator
                        .comparingInt((AllowanceCatalogueEntity a) -> getPriority(a.getCode()))
                        .thenComparing((AllowanceCatalogueEntity a) ->
                                        a.getAnnualLimitFixed() != null ? a.getAnnualLimitFixed() : 0L,
                                Comparator.reverseOrder())
                        .thenComparing(a -> Boolean.TRUE.equals(a.getRequiresBills())))
                .toList();

        // 3. Build recommendations
        List<AllowanceRecommendation> recommendations = new ArrayList<>();
        List<SalaryComponentEntity> newComponents = new ArrayList<>(currentProfile.getComponents());

        TaxSlabPolicyEntity currentSlabPolicy = policyService.getSlabPolicy(taxYear, currentRegime);

        for (AllowanceCatalogueEntity allowance : eligible) {
            if (reduciblePool <= 0) {
                break;
            }

            long suggestedAmount;
            AllowanceLimitEntity limit;
            try {
                limit = resolutionService.resolve(
                        allowance.getCode(), taxYear, currentRegime,
                        currentProfile.getEmployerType() != null ? currentProfile.getEmployerType() : EmployerType.PRIVATE
                ).getLimit();
            } catch (Exception e) {
                log.warn("Could not resolve limit for allowance {} in restructure, skipping: {}",
                        allowance.getCode(), e.getMessage());
                continue;
            }

            if (limit.getAnnualLimitFixed() != null) {
                suggestedAmount = Math.min(limit.getAnnualLimitFixed(), reduciblePool);
            } else if (limit.getLimitFormula() != null) {
                Map<String, Object> vars = buildFormulaVariables(currentProfile);
                try {
                    suggestedAmount = Math.min(formulaEvaluator.evaluate(limit.getLimitFormula(), vars), reduciblePool);
                } catch (Exception e) {
                    log.warn("Failed to evaluate formula for {}: {}", allowance.getCode(), e.getMessage());
                    continue;
                }
            } else {
                suggestedAmount = reduciblePool;
            }

            if (suggestedAmount <= 0) {
                continue;
            }

            reduciblePool -= suggestedAmount;

            long perquisiteValue = computePerquisiteValue(allowance, suggestedAmount, currentProfile, perquisitePolicy);
            long taxFreeBenefit = suggestedAmount - perquisiteValue;

            double marginalRate = getMarginalRate(originalNewResult.getTaxableIncome(), currentSlabPolicy);
            long estimatedSaving = Math.round(taxFreeBenefit * marginalRate);

            recommendations.add(buildRecommendation(allowance, suggestedAmount, estimatedSaving));

            SalaryComponentEntity newComp = new SalaryComponentEntity();
            newComp.setAllowanceCode(allowance.getCode());
            newComp.setAnnualAmount(suggestedAmount);
            newComp.setIsCurrent(false);
            newComp.setNotes("Recommended restructuring");
            newComponents.add(newComp);
        }

        // 4. Build restructured profile
        SalaryProfileEntity restructuredProfile = copyProfile(currentProfile);
        restructuredProfile.setComponents(newComponents);
        restructuredProfile.setProfileType(ProfileType.RESTRUCTURED);

        // 5. Re-run both engines on restructured profile
        TaxComputationEntity.TaxResult newRestructured = engineFactory.getEngine(RegimeType.NEW_REGIME)
                .compute(restructuredProfile,
                        policyService.getSlabPolicy(taxYear, RegimeType.NEW_REGIME),
                        perquisitePolicy, catalogue, formulaEvaluator);
        TaxComputationEntity.TaxResult oldRestructured = engineFactory.getEngine(RegimeType.OLD_REGIME)
                .compute(restructuredProfile,
                        policyService.getSlabPolicy(taxYear, RegimeType.OLD_REGIME),
                        perquisitePolicy, catalogue, formulaEvaluator);

        // 6. Build regime advice
        RegimeAdvice advice = regimeAdvisor.advise(newRestructured, oldRestructured, restructuredProfile);

        long recommendedTax = advice.getRecommendedRegime() == RegimeType.NEW_REGIME
                ? newRestructured.getTotalTax() : oldRestructured.getTotalTax();
        long originalTax = currentRegime == RegimeType.NEW_REGIME ? originalNewRegimeTax : originalOldRegimeTax;
        long totalSaving = Math.abs(originalTax - recommendedTax);

        return RestructuringResult.builder()
                .originalProfile(SalaryProfileResponse.fromEntity(currentProfile))
                .restructuredProfile(SalaryProfileResponse.fromEntity(restructuredProfile))
                .recommendations(recommendations)
                .originalNewRegimeTax(originalNewRegimeTax)
                .originalOldRegimeTax(originalOldRegimeTax)
                .restructuredNewRegimeTax(newRestructured.getTotalTax())
                .restructuredOldRegimeTax(oldRestructured.getTotalTax())
                .totalOptimizedSaving(totalSaving)
                .regimeAdvice(advice)
                .build();
    }

    private long getSpecialAllowance(SalaryProfileEntity profile) {
        if (profile.getComponents() == null) {
            return 0L;
        }
        return profile.getComponents().stream()
                .filter(c -> "SPECIAL_ALLOWANCE".equals(c.getAllowanceCode()))
                .mapToLong(SalaryComponentEntity::getAnnualAmount)
                .sum();
    }

    private int getPriority(String code) {
        return switch (code) {
            case "NPS_EMPLOYER", "MEAL_VOUCHER", "CAB_FACILITY", "HEALTH_CLUB" -> 1;
            case "HRA", "LTA", "CHILDREN_EDUCATION", "HOSTEL_ALLOWANCE" -> 2;
            case "FUEL_ALLOWANCE", "DRIVER_ALLOWANCE", "TELEPHONE", "BOOKS_PERIODICALS" -> 3;
            case "GIFT_VOUCHER", "UNIFORM_ALLOWANCE" -> 4;
            default -> 5;
        };
    }

    private Map<String, Object> buildFormulaVariables(SalaryProfileEntity profile) {
        long basic = getComponentAmount(profile, "BASIC");
        long da = getComponentAmount(profile, "DA");
        Map<String, Object> vars = new HashMap<>();
        vars.put("basic", basic);
        vars.put("da", da);
        vars.put("basic_plus_da", basic + da);
        vars.put("rent_paid", profile.getMonthlyRentPaid() != null ? profile.getMonthlyRentPaid() * 12 : 0L);
        vars.put("hra_received", getComponentAmount(profile, "HRA"));
        vars.put("actual_hra", getComponentAmount(profile, "HRA"));
        return vars;
    }

    private long getComponentAmount(SalaryProfileEntity profile, String code) {
        if (profile.getComponents() == null) {
            return 0L;
        }
        return profile.getComponents().stream()
                .filter(c -> code.equals(c.getAllowanceCode()))
                .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                .sum();
    }

    private long computePerquisiteValue(AllowanceCatalogueEntity allowance, long amount,
            SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
        return switch (allowance.getCode()) {
            case "CAR_COMPANY" -> perquisiteService.computeCarPerquisiteMonthly(
                    profile.getCarOwnership(), profile.getCarEngineSize(),
                    Boolean.TRUE.equals(profile.getDriverProvidedByEmployer()), true, policy) * 12;
            case "DRIVER_ALLOWANCE" -> perquisiteService.computeDriverPerquisiteMonthly(true, policy) * 12;
            default -> 0L;
        };
    }

    private double getMarginalRate(long taxableIncome, TaxSlabPolicyEntity slabPolicy) {
        if (slabPolicy == null || slabPolicy.getSlabs() == null) {
            return 0.30;
        }
        for (TaxSlabPolicyEntity.TaxSlab slab : slabPolicy.getSlabs()) {
            long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
            if (taxableIncome >= slab.getFromAmount() && taxableIncome <= to) {
                return slab.getRatePercent() / 100.0;
            }
        }
        return 0.30;
    }

    private AllowanceRecommendation buildRecommendation(AllowanceCatalogueEntity allowance,
            long suggestedAmount, long estimatedSaving) {
        return AllowanceRecommendation.builder()
                .allowanceCode(allowance.getCode())
                .displayName(allowance.getDisplayName())
                .description(allowance.getDescription())
                .whyItMatters(allowance.getWhyItMatters())
                .suggestedAnnualAmount(suggestedAmount)
                .estimatedTaxSaving(estimatedSaving)
                .priority(getPriority(allowance.getCode()))
                .availabilityPath(allowance.getAvailabilityPath())
                .hrSupportLikelihood(allowance.getHrSupportLikelihood())
                .actionRequired(switch (allowance.getAvailabilityPath()) {
                    case HR_RESTRUCTURE -> "Talk to HR";
                    case SELF_DECLARE_ITR -> "Self-declare in ITR";
                    case BOTH -> "Talk to HR or self-declare in ITR";
                    case NOT_APPLICABLE -> "Auto-exempt — no action needed";
                })
                .hrAskTemplate(allowance.getHrAskTemplate())
                .whatIfHrSaysNo(allowance.getWhatIfHrSaysNo())
                .itrPortalPath(null)
                .documentsRequired(allowance.getDocumentsRequired())
                .documentsToKeep(allowance.getDocumentsToKeep())
                .itSection(allowance.getItSection())
                .eligibilityConditions(allowance.getEligibilityConditions())
                .commonMistakes(allowance.getCommonMistakes())
                .beginnerFaq(List.of())
                .build();
    }

    private SalaryProfileEntity copyProfile(SalaryProfileEntity original) {
        SalaryProfileEntity copy = new SalaryProfileEntity();
        copy.setEmail(original.getEmail());
        copy.setProfileName(original.getProfileName() + " (Restructured)");
        copy.setProfileType(ProfileType.RESTRUCTURED);
        copy.setEmployerName(original.getEmployerName());
        copy.setTaxYear(original.getTaxYear());
        copy.setCityTier(original.getCityTier());
        copy.setCityName(original.getCityName());
        copy.setRegimeType(original.getRegimeType());
        copy.setEmployerType(original.getEmployerType());
        copy.setComponents(new ArrayList<>(original.getComponents()));
        copy.setCarProvided(original.getCarProvided());
        copy.setCarOwnership(original.getCarOwnership());
        copy.setCarEngineSize(original.getCarEngineSize());
        copy.setDriverProvidedByEmployer(Boolean.TRUE.equals(original.getDriverProvidedByEmployer()));
        copy.setDriverSalaryMonthly(original.getDriverSalaryMonthly());
        copy.setInvestment80c(original.getInvestment80c());
        copy.setInvestment80d(original.getInvestment80d());
        copy.setHomeLoanInterest(original.getHomeLoanInterest());
        copy.setNpsSelf80ccd1b(original.getNpsSelf80ccd1b());
        copy.setMonthlyRentPaid(original.getMonthlyRentPaid());
        copy.setIsPayingRent(original.getIsPayingRent());
        copy.setNumberOfChildren(original.getNumberOfChildren());
        copy.setIsPhysicallyDisabled(original.getIsPhysicallyDisabled());
        copy.setLtaBlockStart(original.getLtaBlockStart());
        copy.setLtaTripsClaimedInBlock(original.getLtaTripsClaimedInBlock());
        copy.setLtaCarryForwardAvailable(Boolean.TRUE.equals(original.getLtaCarryForwardAvailable()));
        copy.setStatus(EntityStatus.ACTIVE);
        return copy;
    }
}
