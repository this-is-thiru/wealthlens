package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.AllowanceResolutionService;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * New Regime tax computation engine for FY 2025-26.
 * Extends AbstractTaxEngine and provides regime-specific hooks.
 *
 * Key characteristics:
 * - No HRA, LTA, 80C, 80D, or other Old Regime deductions
 * - Standard deduction of ₹75,000
 * - 87A rebate up to ₹60,000 for income up to ₹12L
 * - Marginal relief when income is between ₹12L and ₹12.75L
 * - Progressive slab rates: 0-4L(0%), 4-8L(5%), 8-12L(10%), 12-16L(15%), 16-20L(20%), 20-24L(25%), 24L+(30%)
 * - Surcharge from ₹50L onwards
 * - Cess at 4%
 */
@Service
@Log4j2
public class NewRegimeTaxEngine extends AbstractTaxEngine {

    private final PerquisiteValuationService perquisiteValuation;

    @Autowired
    public NewRegimeTaxEngine(FormulaEvaluator formulaEvaluator,
                               AllowanceResolutionService resolutionService,
                               PerquisiteValuationService perquisiteValuation) {
        super(formulaEvaluator, resolutionService, perquisiteValuation);
        this.perquisiteValuation = perquisiteValuation;
    }

    @Override
    public RegimeType getRegime() {
        return RegimeType.NEW_REGIME;
    }

    @Override
    public String getSupportedTaxYear() {
        return "2025-26";
    }

    @Override
    protected long getStandardDeduction(long gross, TaxSlabPolicyEntity slabPolicy) {
        return Math.min(getOrDefault(slabPolicy.getStandardDeduction(), 75_000L), gross);
    }

    @Override
    protected ExemptionResult computeExemptions(SalaryProfileEntity profile,
                                                 PerquisitePolicyEntity perquisitePolicy,
                                                 List<String> applied) {
        long total = 0L;
        String taxYear = profile.getTaxYear();
        RegimeType regime = getRegime();
        EmployerType employer = profile.getEmployerType() != null ? profile.getEmployerType() : EmployerType.PRIVATE;

        // ---- Employer PF Exemption ----
        long employerPf = getComponentAmount(profile, "EMPLOYER_PF");
        long basic = getComponentAmount(profile, "BASIC");
        Double pfRate = resolveRatePercent("EMPLOYER_PF", taxYear, getRegime(), employer);
        if (pfRate == null) {
            throw new IllegalArgumentException("No active policy for EMPLOYER_PF in taxYear=" + taxYear);
        }
        double effectivePfRate = pfRate / 100.0;
        long pfExempt = Math.min(employerPf, Math.round(basic * effectivePfRate));
        if (pfExempt > 0) {
            total += pfExempt;
            applied.add("Employer PF exemption: Rs. " + pfExempt);
        }

        // ---- Employer NPS (80CCD2) ----
        long employerNps = getComponentAmount(profile, "NPS_EMPLOYER");
        if (employerNps > 0) {
            try {
                AllowanceLimitEntity npsLimit = resolutionService
                        .resolve("NPS_EMPLOYER", taxYear, regime, employer)
                        .getLimit();
                long npsCap = evaluateLimit(npsLimit.getLimitFormula(), profile);
                long npsExempt = Math.min(employerNps, npsCap);
                total += npsExempt;
                applied.add("Employer NPS (80CCD2): Rs. " + npsExempt);
            } catch (Exception e) {
                log.warn("Failed to resolve NPS employer limit for {}: {}", taxYear, e.getMessage());
            }
        }

        // ---- Meal Voucher ----
        long mealVoucher = getComponentAmount(profile, "MEAL_VOUCHER");
        if (mealVoucher > 0) {
            try {
                AllowanceLimitEntity mealLimit = resolutionService
                        .resolve("MEAL_VOUCHER", taxYear, regime, employer)
                        .getLimit();
                long mealCap;
                if (mealLimit.getAnnualLimitFixed() != null) {
                    mealCap = mealLimit.getAnnualLimitFixed();
                } else {
                    long perMeal = perquisitePolicy.getMealPerMealAmount() != null
                            ? perquisitePolicy.getMealPerMealAmount() : 200;
                    int mealsPerDay = perquisitePolicy.getMealMealsPerDay() != null
                            ? perquisitePolicy.getMealMealsPerDay() : 2;
                    int workingDays = perquisitePolicy.getMealWorkingDaysPerMonth() != null
                            ? perquisitePolicy.getMealWorkingDaysPerMonth() : 22;
                    mealCap = perMeal * mealsPerDay * workingDays * 12L;
                }
                long mealExempt = Math.min(mealVoucher, mealCap);
                total += mealExempt;
                applied.add("Meal Voucher: Rs. " + mealExempt + " exempt");
            } catch (Exception e) {
                log.warn("Failed to resolve meal voucher limit for {}: {}", taxYear, e.getMessage());
                long exemptAmount = perquisiteValuation.computeMealVoucherExemptionAnnual(mealVoucher, perquisitePolicy);
                total += exemptAmount;
                applied.add("Meal Voucher: Rs. " + exemptAmount + " exempt (fallback)");
            }
        }

        // ---- Gift Voucher ----
        long giftVoucher = getComponentAmount(profile, "GIFT_VOUCHER");
        if (giftVoucher > 0) {
            try {
                AllowanceLimitEntity giftLimit = resolutionService
                        .resolve("GIFT_VOUCHER", taxYear, regime, employer)
                        .getLimit();
                long giftCap = getOrDefault(giftLimit.getAnnualLimitFixed(),
                        getOrDefault(perquisitePolicy.getGiftAnnualLimit(), 15_000L));
                long giftExempt = Math.min(giftVoucher, giftCap);
                total += giftExempt;
                applied.add("Gift Voucher: Rs. " + giftExempt + " exempt");
            } catch (Exception e) {
                log.warn("Failed to resolve gift voucher limit for {}: {}", taxYear, e.getMessage());
                long exemptAmount = perquisiteValuation.computeGiftVoucherExemptionAnnual(giftVoucher, perquisitePolicy);
                total += exemptAmount;
                applied.add("Gift Voucher: Rs. " + exemptAmount + " exempt (fallback)");
            }
        }

        return new ExemptionResult(total);
    }

    @Override
    protected DeductionResult computeDeductions(SalaryProfileEntity profile, List<String> applied) {
        // New regime has NO additional Chapter VI-A deductions (80C, 80D, etc.)
        return new DeductionResult(0L);
    }

    @Override
    protected List<String> computeWarnings(SalaryProfileEntity profile,
                                            TaxComputationEntity.TaxResult result,
                                            PerquisitePolicyEntity perquisitePolicy,
                                            TaxSlabPolicyEntity slabPolicy) {
        List<String> warnings = new ArrayList<>();
        String taxYear = profile.getTaxYear();
        EmployerType employer = profile.getEmployerType();

        if (Boolean.TRUE.equals(profile.getEsopPresent())) {
            warnings.add("ESOP/RSU detected — consult a CA for perquisite computation.");
        }

        long employerNps = getComponentAmount(profile, "NPS_EMPLOYER");
        long basic = getComponentAmount(profile, "BASIC");
        Double npsRate = resolveRatePercent("NPS_EMPLOYER", taxYear, getRegime(), employer);
        if (npsRate == null) {
            throw new IllegalArgumentException("No active policy for NPS_EMPLOYER in taxYear=" + taxYear);
        }
        double effectiveNpsRate = npsRate / 100.0;
        if (employerNps > Math.round(basic * effectiveNpsRate)) {
            warnings.add("Employer NPS exceeds " + npsRate + "% of Basic — excess is taxable.");
        }

        long taxableIncome = result.getTaxableIncome() != null ? result.getTaxableIncome() : 0L;
        // Rebate limit comes from slabPolicy (inherited compute() method handles it via slabPolicy.getRebate87aLimit())
        // We just check if taxable income exceeds typical rebate limit for warning purposes
        long rebateLimit = slabPolicy.getRebate87aLimit();
        if (taxableIncome > rebateLimit && result.getRebate87aApplied() != null && result.getRebate87aApplied() > 0) {
            warnings.add("Rebate 87A was applied but taxable income exceeds ₹12L — verify at filing.");
        }

        return warnings;
    }

    @Override
    protected boolean matchesRegime(TaxSlabPolicyEntity.SurchargeSlab slab) {
        return slab.getRegimeType() == RegimeType.NEW_REGIME;
    }

    private long evaluateLimit(String formula, SalaryProfileEntity profile) {
        if (formula == null || formula.isBlank()) {
            return Long.MAX_VALUE;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("basic", getComponentAmount(profile, "BASIC"));
        vars.put("da", getComponentAmount(profile, "DA"));
        vars.put("actual_nps", getComponentAmount(profile, "NPS_EMPLOYER"));
        try {
            return formulaEvaluator.evaluate(formula, vars);
        } catch (Exception e) {
            log.warn("Failed to evaluate formula '{}': {}", formula, e.getMessage());
            return 0L;
        }
    }
}
