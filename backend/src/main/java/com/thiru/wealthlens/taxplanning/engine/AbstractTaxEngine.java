package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.AllowanceResolutionService;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

/**
 * Abstract base class for tax computation engines.
 * Implements the Template Method pattern to extract common computation skeleton
 * while allowing regime-specific override of exemptions, deductions, and warnings.
 */
@Log4j2
public abstract class AbstractTaxEngine implements TaxEngine {

    protected final FormulaEvaluator formulaEvaluator;
    protected final AllowanceResolutionService resolutionService;
    protected final PerquisiteValuationService perquisiteValuation;

    protected AbstractTaxEngine(FormulaEvaluator formulaEvaluator,
                                 AllowanceResolutionService resolutionService,
                                 PerquisiteValuationService perquisiteValuation) {
        this.formulaEvaluator = formulaEvaluator;
        this.resolutionService = resolutionService;
        this.perquisiteValuation = perquisiteValuation;
    }

    @Override
    public TaxComputationEntity.TaxResult compute(
            SalaryProfileEntity profile,
            TaxSlabPolicyEntity slabPolicy,
            PerquisitePolicyEntity perquisitePolicy,
            List<AllowanceCatalogueEntity> catalogue,
            FormulaEvaluator formulaEval
    ) {
        TaxComputationEntity.TaxResult result = new TaxComputationEntity.TaxResult();
        List<String> appliedExemptions = new ArrayList<>();
        List<String> appliedDeductions = new ArrayList<>();

        // ---- Step 1: Gross Salary (common) ----
        long gross = computeGrossSalary(profile, perquisitePolicy);
        result.setGrossSalary(gross);

        // ---- Step 2: Standard Deduction (regime-specific limit) ----
        long standardDeduction = getStandardDeduction(gross, slabPolicy);
        result.setTotalDeductions(standardDeduction);

        // ---- Step 3: Regime-specific exemptions (HRA, LTA, PF, NPS, meal, gift, etc.) ----
        ExemptionResult exemptionResult = computeExemptions(profile, perquisitePolicy, appliedExemptions);
        result.setTotalExemptions(exemptionResult.total);

        // ---- Step 4: Regime-specific deductions (Chapter VI-A, professional tax, etc.) ----
        DeductionResult deductionResult = computeDeductions(profile, appliedDeductions);

        // ---- Step 5: Taxable Income ----
        long totalDeductionsAll = standardDeduction + deductionResult.total;
        result.setTotalDeductions(totalDeductionsAll);
        long taxableIncome = gross - standardDeduction - exemptionResult.total - deductionResult.total;
        result.setTaxableIncome(Math.max(0, taxableIncome));

        // ---- Step 6: Progressive Tax from Slabs (common) ----
        long basicTax = computeProgressiveTax(result.getTaxableIncome(), slabPolicy);
        result.setBasicTaxBeforeRebate(basicTax);

        // ---- Step 7: Rebate 87A (common) ----
        long rebate = computeRebate87A(result.getTaxableIncome(), basicTax, slabPolicy);
        result.setRebate87aApplied(rebate);
        long taxAfterRebate = Math.max(0, basicTax - rebate);

        // ---- Step 8: Marginal Relief (common) ----
        long marginalRelief = computeMarginalRelief(result.getTaxableIncome(), taxAfterRebate, slabPolicy);
        result.setMarginalReliefApplied(marginalRelief > 0);
        taxAfterRebate = Math.max(0, taxAfterRebate - marginalRelief);
        result.setTaxAfterRebate(taxAfterRebate);

        // ---- Step 9: Surcharge (common, but slab filtering is regime-specific) ----
        long surcharge = computeSurcharge(result.getTaxableIncome(), taxAfterRebate, slabPolicy);
        result.setSurcharge(surcharge);

        // ---- Step 10: Cess (common) ----
        long cess = Math.round((taxAfterRebate + surcharge) * getCessRate(slabPolicy) / 100.0);
        result.setCess(cess);

        // ---- Step 11: Total Tax ----
        long totalTax = taxAfterRebate + surcharge + cess;
        result.setTotalTax(totalTax);

        // ---- Step 12: Net Take Home ----
        long employeePf = getComponentAmount(profile, "EMPLOYEE_PF");
        long professionalTax = getComponentAmount(profile, "PROFESSIONAL_TAX");
        result.setNetTakeHome(gross - employeePf - professionalTax - totalTax);

        // ---- Step 13: Warnings (regime-specific hook) ----
        result.setWarnings(new ArrayList<>(computeWarnings(profile, result, perquisitePolicy, slabPolicy)));

        result.setAppliedExemptions(appliedExemptions);
        result.setAppliedDeductions(appliedDeductions);

        return result;
    }

    // ─── Protected abstract hooks (to be overridden by subclasses) ───

    /** Returns the standard deduction limit for this regime. */
    protected abstract long getStandardDeduction(long gross, TaxSlabPolicyEntity slabPolicy);

    /** Computes regime-specific exemptions and populates applied list. */
    protected abstract ExemptionResult computeExemptions(SalaryProfileEntity profile,
                                                          PerquisitePolicyEntity perquisitePolicy,
                                                          List<String> applied);

    /** Computes regime-specific deductions (Chapter VI-A, professional tax, etc.) */
    protected abstract DeductionResult computeDeductions(SalaryProfileEntity profile,
                                                          List<String> applied);

    /** Computes regime-specific warnings. */
    protected abstract List<String> computeWarnings(SalaryProfileEntity profile,
                                                     TaxComputationEntity.TaxResult result,
                                                     PerquisitePolicyEntity perquisitePolicy,
                                                     TaxSlabPolicyEntity slabPolicy);

    /** Returns true if the surcharge slab belongs to this regime. */
    protected abstract boolean matchesRegime(TaxSlabPolicyEntity.SurchargeSlab slab);

    // ─── Common implementations ───

    protected long computeGrossSalary(SalaryProfileEntity profile, PerquisitePolicyEntity perquisitePolicy) {
        long base = profile.getComponents() == null ? 0L :
                profile.getComponents().stream()
                        .mapToLong(SalaryComponentEntity::getAnnualAmount)
                        .sum();
        long car = computeCarPerquisiteAnnual(profile, perquisitePolicy);
        long driver = computeDriverPerquisiteAnnual(profile, perquisitePolicy);
        return base + car + driver;
    }

    protected long computeProgressiveTax(long taxableIncome, TaxSlabPolicyEntity slabPolicy) {
        if (taxableIncome <= 0 || slabPolicy.getSlabs() == null) {
            return 0L;
        }
        long tax = 0L;
        for (TaxSlabPolicyEntity.TaxSlab slab : slabPolicy.getSlabs()) {
            long from = getOrDefault(slab.getFromAmount(), 0L);
            long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
            if (taxableIncome > from) {
                long taxableInSlab = Math.min(taxableIncome, to) - from;
                if (taxableInSlab > 0) {
                    tax += Math.round(taxableInSlab * slab.getRatePercent() / 100.0);
                }
            }
        }
        return tax;
    }

    protected long computeRebate87A(long taxableIncome, long basicTax, TaxSlabPolicyEntity slabPolicy) {
        long rebateLimit = getOrDefault(slabPolicy.getRebate87aLimit(), 0L);
        long rebateAmount = getOrDefault(slabPolicy.getRebate87aAmount(), 0L);
        if (taxableIncome <= rebateLimit && basicTax > 0) {
            return Math.min(basicTax, rebateAmount);
        }
        return 0L;
    }

    protected long computeMarginalRelief(long taxableIncome, long taxAfterRebate, TaxSlabPolicyEntity slabPolicy) {
        long rebateLimit = getOrDefault(slabPolicy.getRebate87aLimit(), 0L);
        long excess = taxableIncome - rebateLimit;
        if (excess > 0 && taxAfterRebate > excess) {
            return taxAfterRebate - excess;
        }
        return 0L;
    }

    protected long computeSurcharge(long taxableIncome, long taxAfterRelief, TaxSlabPolicyEntity slabPolicy) {
        if (slabPolicy.getSurchargeSlabs() == null) {
            return 0L;
        }
        for (TaxSlabPolicyEntity.SurchargeSlab slab : slabPolicy.getSurchargeSlabs()) {
            if (!matchesRegime(slab)) {
                continue;
            }
            long from = getOrDefault(slab.getFromAmount(), 0L);
            long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
            if (taxableIncome >= from && taxableIncome <= to) {
                return Math.round(taxAfterRelief * slab.getRatePercent() / 100.0);
            }
        }
        return 0L;
    }

    protected double getCessRate(TaxSlabPolicyEntity slabPolicy) {
        return slabPolicy.getCessPercentage() != null ? slabPolicy.getCessPercentage() : 4.0;
    }

    // ─── Perquisite helpers (common) ───

    protected long computeCarPerquisiteAnnual(SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
        if (!Boolean.TRUE.equals(profile.getCarProvided())) {
            return 0L;
        }
        return perquisiteValuation.computeCarPerquisiteMonthly(
                profile.getCarOwnership(), profile.getCarEngineSize(),
                Boolean.TRUE.equals(profile.getDriverProvidedByEmployer()),
                true, policy) * 12;
    }

    protected long computeDriverPerquisiteAnnual(SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
        if (!Boolean.TRUE.equals(profile.getDriverProvidedByEmployer())) {
            return 0L;
        }
        return getOrDefault(policy.getDriverPerquisiteMonthly(), 0L) * 12;
    }

    // ─── Component helpers (common) ───

    protected long getComponentAmount(SalaryProfileEntity profile, String code) {
        if (profile.getComponents() == null) {
            return 0L;
        }
        return profile.getComponents().stream()
                .filter(c -> code.equals(c.getAllowanceCode()))
                .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                .sum();
    }

    protected boolean hasComponent(SalaryProfileEntity profile, String code) {
        if (profile.getComponents() == null) {
            return false;
        }
        return profile.getComponents().stream()
                .anyMatch(c -> code.equals(c.getAllowanceCode()));
    }

    protected long getOrDefault(Long value, long defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Resolves an allowance limit from the policy database.
     * Falls back gracefully if the specific policy is not found.
     */
    protected Long resolveAnnualLimit(String allowanceCode,
                                       String taxYear,
                                       RegimeType regime,
                                       EmployerType employer) {
        try {
            AllowanceLimitEntity limit = resolutionService
                    .resolve(allowanceCode, taxYear, regime, employer)
                    .getLimit();
            return limit.getAnnualLimitFixed();
        } catch (Exception e) {
            log.warn("Could not resolve annual limit for {}: {}", allowanceCode, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves a rate percent from the policy database.
     */
    protected Double resolveRatePercent(String allowanceCode,
                                         String taxYear,
                                         RegimeType regime,
                                         EmployerType employer) {
        try {
            AllowanceLimitEntity limit = resolutionService
                    .resolve(allowanceCode, taxYear, regime, employer)
                    .getLimit();
            return limit.getRatePercent();
        } catch (Exception e) {
            log.warn("Could not resolve rate for {}: {}", allowanceCode, e.getMessage());
            return null;
        }
    }

    // ─── Inner result classes ───

    @Value
    @AllArgsConstructor
    public static class ExemptionResult {
        long total;
    }

    @Value
    @AllArgsConstructor
    public static class DeductionResult {
        long total;
    }
}
