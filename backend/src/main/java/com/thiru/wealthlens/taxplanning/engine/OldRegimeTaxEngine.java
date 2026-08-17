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
 * Old Regime tax computation engine for FY 2025-26.
 * Extends AbstractTaxEngine and provides regime-specific hooks.
 *
 * Key characteristics:
 * - Standard deduction of ₹50,000
 * - HRA exemption via HraExemptionCalculator
 * - LTA exemption (actual travel cost from components)
 * - Section 80C: max ₹1,50,000
 * - Section 80D: max ₹25,000 (self) + ₹25,000 (parent) = ₹50,000
 * - Section 80CCD(1B): max ₹50,000
 * - Section 80CCD(2): employer NPS (formula-based)
 * - Section 24(b): home loan interest max ₹2,00,000
 * - Professional tax: actual amount from profile
 * - Children education: ₹3,000/child/month
 * - Surcharge from ₹50L onwards
 * - Cess at 4%
 */
@Service
@Log4j2
public class OldRegimeTaxEngine extends AbstractTaxEngine {

    private final HraExemptionCalculator hraExemptionCalculator;
    private final PerquisiteValuationService perquisiteValuation;

    @Autowired
    public OldRegimeTaxEngine(FormulaEvaluator formulaEvaluator,
                               AllowanceResolutionService resolutionService,
                               HraExemptionCalculator hraExemptionCalculator,
                               PerquisiteValuationService perquisiteValuation) {
        super(formulaEvaluator, resolutionService, perquisiteValuation);
        this.hraExemptionCalculator = hraExemptionCalculator;
        this.perquisiteValuation = perquisiteValuation;
    }

    @Override
    public RegimeType getRegime() {
        return RegimeType.OLD_REGIME;
    }

    @Override
    public String getSupportedTaxYear() {
        return "2025-26";
    }

    @Override
    protected long getStandardDeduction(long gross, TaxSlabPolicyEntity slabPolicy) {
        return Math.min(slabPolicy.getStandardDeduction(), gross);
    }

    @Override
    protected ExemptionResult computeExemptions(SalaryProfileEntity profile,
                                                 PerquisitePolicyEntity perquisitePolicy,
                                                 List<String> applied) {
        long total = 0L;
        String taxYear = profile.getTaxYear();
        RegimeType regime = getRegime();
        EmployerType employer = profile.getEmployerType();

        // ---- HRA Exemption ----
        long hraExempt = computeHraExemption(profile, applied);
        total += hraExempt;

        // ---- LTA Exemption ----
        long ltaExempt = computeLtaExemption(profile, applied);
        total += ltaExempt;

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
            long exemptAmount = perquisiteValuation.computeMealVoucherExemptionAnnual(mealVoucher, perquisitePolicy);
            total += exemptAmount;
            applied.add("Meal Voucher: Rs. " + exemptAmount + " exempt");
        }

        // ---- Gift Voucher ----
        long giftVoucher = getComponentAmount(profile, "GIFT_VOUCHER");
        if (giftVoucher > 0) {
            long exemptAmount = perquisiteValuation.computeGiftVoucherExemptionAnnual(giftVoucher, perquisitePolicy);
            total += exemptAmount;
            applied.add("Gift Voucher: Rs. " + exemptAmount + " exempt");
        }

        // ---- Children Education ----
        long childEdExempt = computeChildrenEducationExemption(profile, applied);
        total += childEdExempt;

        // ---- Hostel Expenditure ----
        long hostelExempt = computeHostelExemption(profile, applied);
        total += hostelExempt;

        // ---- Books & Periodicals ----
        long booksAmount = getComponentAmount(profile, "BOOKS_PERIODICALS");
        if (booksAmount > 0) {
            total += booksAmount;
            applied.add("Books & Periodicals: Rs. " + booksAmount);
        }

        // ---- Telephone/Mobile ----
        long telephoneAmount = getComponentAmount(profile, "TELEPHONE");
        if (telephoneAmount > 0) {
            total += telephoneAmount;
            applied.add("Telephone/Mobile: Rs. " + telephoneAmount);
        }

        // ---- Uniform Allowance ----
        long uniformAmount = getComponentAmount(profile, "UNIFORM");
        if (uniformAmount > 0) {
            total += uniformAmount;
            applied.add("Uniform Allowance: Rs. " + uniformAmount);
        }

        return new ExemptionResult(total);
    }

    @Override
    protected DeductionResult computeDeductions(SalaryProfileEntity profile, List<String> applied) {
        long total = 0L;
        String taxYear = profile.getTaxYear();
        EmployerType employer = profile.getEmployerType();

        // ---- Section 80C ----
        long investment80c = getOrDefault(profile.getInvestment80c(), 0L);
        Long section80cLimit = resolveAnnualLimit("80C", taxYear, getRegime(), employer);
        if (section80cLimit == null) {
            throw new IllegalArgumentException("No active policy for 80C in taxYear=" + taxYear);
        }
        long effective80cLimit = section80cLimit;
        long section80c = Math.min(investment80c, effective80cLimit);
        if (section80c > 0) {
            total += section80c;
            applied.add("Section 80C: Rs. " + section80c);
        }

        // ---- Section 80D ----
        long investment80d = getOrDefault(profile.getInvestment80d(), 0L);
        Long section80dLimit = resolveAnnualLimit("80D", taxYear, getRegime(), employer);
        if (section80dLimit == null) {
            throw new IllegalArgumentException("No active policy for 80D in taxYear=" + taxYear);
        }
        long effective80dLimit = section80dLimit;
        long section80d = Math.min(investment80d, effective80dLimit);
        if (section80d > 0) {
            total += section80d;
            applied.add("Section 80D: Rs. " + section80d);
        }

        // ---- Section 80CCD(1B) ----
        long npsSelf80ccd1b = getOrDefault(profile.getNpsSelf80ccd1b(), 0L);
        Long section80ccd1bLimit = resolveAnnualLimit("80CCD_1B", taxYear, getRegime(), employer);
        if (section80ccd1bLimit == null) {
            throw new IllegalArgumentException("No active policy for 80CCD_1B in taxYear=" + taxYear);
        }
        long effective80ccd1bLimit = section80ccd1bLimit;
        long section80ccd1b = Math.min(npsSelf80ccd1b, effective80ccd1bLimit);
        if (section80ccd1b > 0) {
            total += section80ccd1b;
            applied.add("Section 80CCD(1B) NPS Self: Rs. " + section80ccd1b);
        }

        // ---- Section 24(b) Home Loan Interest ----
        long homeLoanInterest = getOrDefault(profile.getHomeLoanInterest(), 0L);
        Long section24bLimit = resolveAnnualLimit("24B", taxYear, getRegime(), employer);
        if (section24bLimit == null) {
            throw new IllegalArgumentException("No active policy for 24B in taxYear=" + taxYear);
        }
        long effective24bLimit = section24bLimit;
        long section24b = Math.min(homeLoanInterest, effective24bLimit);
        if (section24b > 0) {
            total += section24b;
            applied.add("Section 24(b) Home Loan Interest: Rs. " + section24b);
        }

        // ---- Professional Tax ----
        long professionalTax = getComponentAmount(profile, "PROFESSIONAL_TAX");
        if (professionalTax > 0) {
            total += professionalTax;
            applied.add("Professional Tax: Rs. " + professionalTax);
        }

        return new DeductionResult(total);
    }

    @Override
    protected List<String> computeWarnings(SalaryProfileEntity profile,
                                            TaxComputationEntity.TaxResult result,
                                            PerquisitePolicyEntity perquisitePolicy,
                                            TaxSlabPolicyEntity slabPolicy) {
        List<String> warnings = new ArrayList<>();

        if (Boolean.TRUE.equals(profile.getEsopPresent())) {
            warnings.add("ESOP/RSU detected — consult a CA for perquisite computation.");
        }

        long monthlyRent = getOrDefault(profile.getMonthlyRentPaid(), 0L);
        if (monthlyRent > 0 && !Boolean.TRUE.equals(profile.getIsPayingRent())) {
            warnings.add("Monthly rent is set but isPayingRent is false — HRA exemption may not apply.");
        }

        if (monthlyRent * 12 > 1_00_000 && !Boolean.TRUE.equals(profile.getIsPayingRent())) {
            warnings.add("Annual rent exceeds Rs. 1,00,000. Ensure landlord PAN is collected for HRA claim.");
        }

        return warnings;
    }

    @Override
    protected boolean matchesRegime(TaxSlabPolicyEntity.SurchargeSlab slab) {
        return slab.getRegimeType() == RegimeType.OLD_REGIME;
    }

    // ─── Private helpers ───

    private long computeHraExemption(SalaryProfileEntity profile, List<String> applied) {
        long actualHraAnnual = getComponentAmount(profile, "HRA");
        if (actualHraAnnual == 0) {
            return 0L;
        }

        long basicSalaryAnnual = getComponentAmount(profile, "BASIC");
        long monthlyRent = getOrDefault(profile.getMonthlyRentPaid(), 0L);
        long rentPaidAnnual = monthlyRent * 12;
        boolean isMetro = Boolean.TRUE.equals(profile.getIsMetroCity());
        boolean isPayingRent = Boolean.TRUE.equals(profile.getIsPayingRent());

        HraExemptionCalculator.HraResult hraResult = hraExemptionCalculator.computeHraExemption(
                actualHraAnnual, basicSalaryAnnual, rentPaidAnnual, isMetro, isPayingRent
        );

        applied.add("HRA Exemption: Rs. " + hraResult.getExemptionAmount());
        if (hraResult.getWarnings() != null) {
            // warnings are handled in computeWarnings
        }

        return hraResult.getExemptionAmount();
    }

    private long computeLtaExemption(SalaryProfileEntity profile, List<String> applied) {
        long actualLta = getComponentAmount(profile, "LTA");
        if (actualLta == 0) {
            return 0L;
        }
        applied.add("LTA Exemption: Rs. " + actualLta);
        return actualLta;
    }

    private long computeChildrenEducationExemption(SalaryProfileEntity profile, List<String> applied) {
        int numChildren = intValueOrDefault(profile.getNumberOfChildren(), 0);
        if (numChildren == 0) {
            return 0L;
        }
        String taxYear = profile.getTaxYear();
        AllowanceLimitEntity eduLimit = resolutionService
                .resolve("CHILDREN_EDUCATION", taxYear, getRegime(), profile.getEmployerType())
                .getLimit();
        long edMonthly = getOrDefault(eduLimit.getMonthlyLimitFixed(), 0L);
        int maxChildren = getOrDefault(eduLimit.getAnnualLimitFixed(), 0L) > 0
                ? (int)(eduLimit.getAnnualLimitFixed() / edMonthly / 12) : 2;
        int cappedChildren = Math.min(numChildren, maxChildren);
        long exemptAmount = cappedChildren * edMonthly * 12;
        applied.add("Children Education Exemption: Rs. " + exemptAmount + " (" + cappedChildren + " child(ren))");
        return exemptAmount;
    }

    private long computeHostelExemption(SalaryProfileEntity profile, List<String> applied) {
        int numChildren = intValueOrDefault(profile.getNumberOfChildren(), 0);
        if (numChildren == 0) {
            return 0L;
        }
        String taxYear = profile.getTaxYear();
        AllowanceLimitEntity hostelLimit = resolutionService
                .resolve("HOSTEL_ALLOWANCE", taxYear, getRegime(), profile.getEmployerType())
                .getLimit();
        long hostelMonthly = getOrDefault(hostelLimit.getMonthlyLimitFixed(), 0L);
        int maxChildren = getOrDefault(hostelLimit.getAnnualLimitFixed(), 0L) > 0
                ? (int)(hostelLimit.getAnnualLimitFixed() / hostelMonthly / 12) : 2;
        int cappedChildren = Math.min(numChildren, maxChildren);
        long exemptAmount = cappedChildren * hostelMonthly * 12;
        applied.add("Hostel Expenditure Exemption: Rs. " + exemptAmount + " (" + cappedChildren + " child(ren))");
        return exemptAmount;
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

    private int intValueOrDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}
