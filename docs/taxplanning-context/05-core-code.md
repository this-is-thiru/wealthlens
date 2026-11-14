# 05 — Core computation code, verbatim

Complete method bodies for the classes a reviewer needs to read in full. Extracted directly from
the source at commit `55024ad`; line ranges are given for each block. Nothing here is paraphrased,
reformatted or abridged.

Requested topics and where they are:

| Topic | Section |
|---|---|
| Tax calculation (the shared template) | §1 `AbstractTaxEngine` |
| Tax calculation — new regime | §2 `NewRegimeTaxEngine` |
| Tax calculation — old regime, deductions | §3 `OldRegimeTaxEngine` |
| HRA | §4 `HraExemptionCalculator` |
| Marginal relief | §5 `MarginalReliefCalculator` (**no production caller**) |
| Deductions, allowance limits, formulas | §6 `FormulaEvaluator`, §7 `AllowanceResolutionService` |
| Perquisites | §8 `PerquisiteValuationService` |
| Regime comparison | §9 `TaxComputationService`, §10 `RegimeAdvisor` |
| Planning / what-if | §11 `RestructuringEngine` |
| **Form 16 parsing** | **Does not exist** — see §12 |
| **TDS projection** | **Does not exist** — see §12 |

---

## 1. `AbstractTaxEngine` — the 13-step template and every shared helper

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/AbstractTaxEngine.java` lines 39–298.

The whole class body from `compute` onward. This is the single place where gross, taxable income, slab tax, rebate, marginal relief, surcharge, cess, total tax and net take-home are computed for **both** regimes.

```java
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
```

## 2. `NewRegimeTaxEngine` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/NewRegimeTaxEngine.java` lines 19–214.

Includes the class Javadoc, which states `Surcharge from ₹50L onwards` — see Observation O-3.

```java
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
```

## 3. `OldRegimeTaxEngine` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/OldRegimeTaxEngine.java` lines 19–350.

Includes `computeExemptions` (HRA, LTA, employer PF, employer NPS, meal, gift, children education, hostel, books, telephone, uniform), `computeDeductions` (80C, 80D, 80CCD(1B), 24(b), professional tax), `computeWarnings`, and the private helpers.

```java
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
```

## 4. `HraExemptionCalculator` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/HraExemptionCalculator.java` lines 10–122.

`computeHraExemption` (`:41-78`) is the method the old-regime engine calls. `calculate` (`:84-101`) and `calculateFull` (`:106-121`) have **no caller anywhere** and are included because their comments describe an intended implementation — see `04-constants-tests-history.md` §4.

```java
@Component
@Log4j2
public class HraExemptionCalculator {

    private static final long DAYS_PER_MONTH = 30;
    private static final long MONTHS_PER_YEAR = 12;
    private static final List<String> METRO_CITIES = List.of(
            "Mumbai", "Delhi", "Kolkata", "Chennai",
            "Hyderabad", "Bengaluru", "Pune", "Ahmedabad"
    );

    /**
     * Result of HRA exemption computation.
     */
    @Data
    @AllArgsConstructor
    public static class HraResult {
        private long exemptionAmount;
        private List<String> warnings;
    }

    /**
     * Computes HRA exemption for a given set of inputs.
     *
     * @param actualHraReceivedAnnual Actual HRA received in the year
     * @param basicSalaryAnnual       Basic salary (annual)
     * @param rentPaidAnnual          Annual rent paid
     * @param isMetroCity             Whether the employee works in a metro city
     * @param isPayingRent            Whether the employee is currently paying rent
     * @return HraResult containing exemption amount and any warnings
     */
    public HraResult computeHraExemption(
            long actualHraReceivedAnnual,
            long basicSalaryAnnual,
            long rentPaidAnnual,
            boolean isMetroCity,
            boolean isPayingRent
    ) {
        List<String> warnings = new ArrayList<>();

        // Condition 1: Not paying rent → no exemption
        if (!isPayingRent || rentPaidAnnual == 0) {
            warnings.add("HRA exemption is Rs. 0 because the employee is not paying rent or rent amount is zero.");
            return new HraResult(0L, warnings);
        }

        // Condition 2: Rent > ₹1,00,000 with no landlord PAN (Phase 1.5 enhancement)
        if (rentPaidAnnual > 100_000) {
            warnings.add("Annual rent exceeds Rs. 1,00,000. Ensure landlord PAN is collected and submitted to employer for HRA claim.");
        }

        // Metro: 50% of basic salary, Non-metro: 40%
        long metroHra = Math.round(basicSalaryAnnual * 50.0 / 100.0);
        long nonMetroHra = Math.round(basicSalaryAnnual * 40.0 / 100.0);
        long selectedPercentageHra = isMetroCity ? metroHra : nonMetroHra;

        // Actual rent condition: rent paid minus 10% of basic
        long rentCondition = Math.max(0L, rentPaidAnnual - Math.round(basicSalaryAnnual * 10.0 / 100.0));

        long exemptAmount = Math.min(
                Math.min(
                        Math.min(actualHraReceivedAnnual, selectedPercentageHra),
                        rentCondition
                ),
                actualHraReceivedAnnual
        );

        return new HraResult(exemptAmount, warnings);
    }

    /**
     * Calculates HRA exemption using the 3-way minimum rule.
     * Exemption = min(hraReceived, rentPaid - 10% of baseSalary, 40%/50% of baseSalary depending on metro)
     */
    public long calculate(long hraReceived, long monthlyRent, boolean isMetro) {
        // Basic + DA from profile would be needed for accurate calculation
        // Using simplified approach: min of all three components
        // For proper calculation, we need actual basic salary which is in profile components

        long annualRent = monthlyRent * MONTHS_PER_YEAR;
        long annualHra = hraReceived;

        // Return HRA received as exemption (simplified)
        // Full implementation would compute: min(hra, rent - 10%basic, 50%/40% of basic)
        if (annualHra == 0) {
            return 0L;
        }

        // Placeholder: return HRA received up to rent paid
        // Proper implementation requires basic salary component from profile
        return Math.min(annualHra, annualRent);
    }

    /**
     * Full HRA calculation with basic salary component.
     */
    public long calculateFull(long hraReceived, long monthlyRent, long annualBasic, boolean isMetro) {
        if (hraReceived <= 0 || monthlyRent <= 0) {
            return 0L;
        }

        long annualRent = monthlyRent * MONTHS_PER_YEAR;
        long tenPercentOfBasic = (long) (annualBasic * 0.10);
        long rentMinusTenPercent = Math.max(0, annualRent - tenPercentOfBasic);
        long metroPercent = isMetro ? 50 : 40;
        long percentOfBasic = (long) (annualBasic * metroPercent / 100.0);

        long exemption1 = Math.min(hraReceived, rentMinusTenPercent);
        long exemption2 = Math.min(hraReceived, percentOfBasic);

        return Math.min(exemption1, exemption2);
    }
}
```

## 5. `MarginalReliefCalculator` — complete class (NO PRODUCTION CALLER)

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/MarginalReliefCalculator.java` lines 8–81.

Seven tests exercise this class; nothing in production constructs it. The marginal relief that actually runs is `AbstractTaxEngine.computeMarginalRelief` in §1 (`:175-182`), a different implementation. See Observation O-2.

```java
@Component
@Log4j2
public class MarginalReliefCalculator {

    /**
     * Result of a marginal relief computation.
     */
    @Data
    @AllArgsConstructor
    public static class MarginalReliefResult {
        private long relievedTax;
        private long reliefAmount;
        private boolean reliefApplied;
    }

    /**
     * Applies marginal relief when taxable income crosses the rebate limit.
     * Tax cannot exceed (income - rebate_limit) + 10% of excess over rebate limit.
     */
    public long applyMarginalRelief(long taxBeforeRebate, long taxableIncome, long rebateLimit) {
        if (taxableIncome <= rebateLimit) {
            return taxBeforeRebate;
        }

        long incomeOverRebateLimit = taxableIncome - rebateLimit;
        long maxTax = incomeOverRebateLimit;

        if (taxBeforeRebate > maxTax) {
            log.debug("Applying marginal relief: tax {} capped to {} for income {} over limit {}",
                    taxBeforeRebate, maxTax, taxableIncome, rebateLimit);
            return maxTax;
        }

        return taxBeforeRebate;
    }

    /**
     * Checks if marginal relief is applicable (income slightly above rebate limit with high tax).
     */
    public boolean isMarginalReliefApplicable(long taxBeforeRebate, long taxableIncome, long rebateLimit) {
        if (taxableIncome <= rebateLimit) {
            return false;
        }
        long incomeOverRebateLimit = taxableIncome - rebateLimit;
        return taxBeforeRebate > incomeOverRebateLimit;
    }

    /**
     * Computes rebate marginal relief for new regime 87A.
     * Tax is capped at: taxableIncome - rebateLimit
     *
     * @param taxableIncome Total taxable income
     * @param computedTax   Tax computed before rebate
     * @param rebateLimit   Income limit for 87A rebate
     * @param rebateAmount  Maximum rebate amount
     * @return MarginalReliefResult with relievedTax, reliefAmount, reliefApplied
     */
    public MarginalReliefResult computeRebateMarginalRelief(
            long taxableIncome,
            long computedTax,
            long rebateLimit,
            long rebateAmount
    ) {
        if (taxableIncome <= rebateLimit) {
            return new MarginalReliefResult(computedTax, 0L, false);
        }
        long maxTaxAtLimit = taxableIncome - rebateLimit;
        if (maxTaxAtLimit < computedTax) {
            long reliefAmount = computedTax - maxTaxAtLimit;
            return new MarginalReliefResult(maxTaxAtLimit, reliefAmount, true);
        }
        return new MarginalReliefResult(computedTax, 0L, false);
    }
}
```

## 6. `FormulaEvaluator` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/FormulaEvaluator.java` lines 11–61.

The class Javadoc records the security history behind commit `5eaeb98`. Note that variables are bound via `context::setVariable`, which SpEL reads as `#name` — see Observation O-1.

```java
/**
 * Evaluates the allowance and exemption formulas carried by tax policy data.
 *
 * <p>Owned by this module outright. The charges engine solves a similar problem with its own
 * evaluator; neither depends on the other, and the contracts differ — this returns whole rupees
 * where that returns money to two decimals, and the vocabularies are entirely different. Only the
 * safety floor is shared, through {@link SafeExpressions}.
 *
 * <p><b>Formulas here are data.</b> {@code AllowanceCatalogueEntity.limitFormula} is written
 * through {@code PUT /tax-planning/admin/allowances/{code}} and seeded from JSON, so an expression
 * evaluated here may have arrived over the wire. It is therefore evaluated in a read-only context
 * that cannot resolve a type, a bean or a constructor — previously it was
 * {@code StandardEvaluationContext}, under which {@code T(java.lang.Runtime).getRuntime().exec(…)}
 * was a valid allowance limit.
 */
@Component
public class FormulaEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * The formula's value, or zero when there is none.
     *
     * <p>The overload that accepted a caller-supplied {@code EvaluationContext} is gone. It had no
     * callers, and it was the hole this class could not close for itself: a caller passing a
     * {@code StandardEvaluationContext} would have reopened everything below.
     */
    public long evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.isBlank()) {
            return 0L;
        }
        SafeExpressions.reject(formula);
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        variables.forEach(context::setVariable);
        return evaluate(formula, context);
    }

    /** The house-rent vocabulary, which is fixed rather than policy-supplied. */
    public long evaluate(String formula, Long basic, Long da, Long rentPaid, Long hraReceived) {
        return evaluate(formula, Map.of(
                "basic", basic != null ? basic : 0L,
                "da", da != null ? da : 0L,
                "rentPaid", rentPaid != null ? rentPaid : 0L,
                "hraReceived", hraReceived != null ? hraReceived : 0L));
    }

    private long evaluate(String formula, EvaluationContext context) {
        Long value = parser.parseExpression(formula).getValue(context, Long.class);
        return value == null ? 0L : value;
    }
}
```

## 7. `AllowanceResolutionService` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/policy/service/AllowanceResolutionService.java` lines 18–59.

The three-tier limit fallback, and `findAvailableAllowanceCodes`, which `RestructuringEngine` intersects against the catalogue.

```java
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
```

## 8. `PerquisiteValuationService` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/engine/PerquisiteValuationService.java` lines 10–186.

`computeCarPerquisiteMonthly` (`:94-130`), `computeDriverPerquisiteMonthly` (`:139-148`), `computeMealVoucherExemptionAnnual` (`:157-170`) and `computeGiftVoucherExemptionAnnual` (`:179-185`) are live. `calculateCarPerquisite` (`:18-43`), `calculateDriverPerquisite` (`:48-59`) and `calculateMealPerquisite` (`:65-76`) have **no caller** and use different fallback constants from the live methods — ₹96,000/₹1,20,000/₹60,000/₹84,000 annual versus ₹8,000/₹10,000/₹5,000/₹7,000 monthly.

```java
@Service
@Log4j2
public class PerquisiteValuationService {

    /**
     * Calculates car perquisite value based on engine size and driver availability.
     * Perquisite = value as per policy (monthly) * 12
     */
    public long calculateCarPerquisite(SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
        if (Boolean.FALSE.equals(profile.getCarProvided())) {
            return 0L;
        }

        boolean hasDriver = Boolean.TRUE.equals(profile.getDriverProvidedByEmployer());
        boolean isSmallEngine = isSmallEngine(profile.getCarEngineSize());

        if (hasDriver) {
            if (isSmallEngine) {
                return policy.getCarLeq1600ccOrEvWithDriver() != null
                        ? policy.getCarLeq1600ccOrEvWithDriver() * 12 : 96000L;
            } else {
                return policy.getCarGt1600ccWithDriver() != null
                        ? policy.getCarGt1600ccWithDriver() * 12 : 120000L;
            }
        } else {
            if (isSmallEngine) {
                return policy.getCarLeq1600ccOrEvNoDriver() != null
                        ? policy.getCarLeq1600ccOrEvNoDriver() * 12 : 60000L;
            } else {
                return policy.getCarGt1600ccNoDriver() != null
                        ? policy.getCarGt1600ccNoDriver() * 12 : 84000L;
            }
        }
    }

    /**
     * Calculates driver perquisite value.
     */
    public long calculateDriverPerquisite(SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
        if (Boolean.FALSE.equals(profile.getDriverProvidedByEmployer())) {
            return 0L;
        }
        long driverSalaryMonthly = profile.getDriverSalaryMonthly() != null ? profile.getDriverSalaryMonthly() : 0L;
        long policyDriverValue = policy.getDriverPerquisiteMonthly() != null
                ? policy.getDriverPerquisiteMonthly() : 3000L;
        // Perquisite = actual salary paid by employer - exempt limit
        long exemptLimit = policyDriverValue * 12;
        long actualSalaryPaid = driverSalaryMonthly * 12;
        return Math.max(0, actualSalaryPaid - exemptLimit);
    }

    /**
     * Calculates meal voucher perquisite.
     * Exempt up to ₹200 per meal, 2 meals per day, 22 working days per month.
     */
    public long calculateMealPerquisite(long monthlyMealVoucherAmount, PerquisitePolicyEntity policy) {
        long perMealExempt = policy.getMealPerMealAmount() != null ? policy.getMealPerMealAmount() : 200L;
        long mealsPerDay = policy.getMealMealsPerDay() != null ? policy.getMealMealsPerDay() : 2L;
        long workingDaysPerMonth = policy.getMealWorkingDaysPerMonth() != null
                ? policy.getMealWorkingDaysPerMonth() : 22L;

        long monthlyExempt = perMealExempt * mealsPerDay * workingDaysPerMonth;
        long annualExempt = monthlyExempt * 12;
        long annualProvided = monthlyMealVoucherAmount * 12;

        return Math.min(annualProvided, annualExempt);
    }

    private boolean isSmallEngine(CarEngineSize engineSize) {
        return engineSize == CarEngineSize.LEQ_1600CC_OR_EV;
    }

    /**
     * Computes monthly car perquisite value.
     * Returns the monthly rate; the caller multiplies by 12 for annual.
     * Employee-owned cars: no perquisite.
     *
     * @param ownership         Car ownership type
     * @param engineSize        Engine size category
     * @param driverByEmployer  Whether driver is provided by employer
     * @param usedPartlyPersonal Whether car is used partly for personal use
     * @param policy            Perquisite policy with rate limits
     * @return Monthly perquisite value in rupees
     */
    public long computeCarPerquisiteMonthly(
            CarOwnership ownership,
            CarEngineSize engineSize,
            boolean driverByEmployer,
            boolean usedPartlyPersonal,
            PerquisitePolicyEntity policy
    ) {
        // Employee-owned: no perquisite
        if (ownership == CarOwnership.EMPLOYEE_OWNED) {
            return 0L;
        }

        // If not used for personal use at all, no perquisite
        if (!usedPartlyPersonal) {
            return 0L;
        }

        boolean isSmallEngine = isSmallEngine(engineSize);

        if (driverByEmployer) {
            if (isSmallEngine) {
                return policy.getCarLeq1600ccOrEvWithDriver() != null
                        ? policy.getCarLeq1600ccOrEvWithDriver() : 8000L;
            } else {
                return policy.getCarGt1600ccWithDriver() != null
                        ? policy.getCarGt1600ccWithDriver() : 10000L;
            }
        } else {
            if (isSmallEngine) {
                return policy.getCarLeq1600ccOrEvNoDriver() != null
                        ? policy.getCarLeq1600ccOrEvNoDriver() : 5000L;
            } else {
                return policy.getCarGt1600ccNoDriver() != null
                        ? policy.getCarGt1600ccNoDriver() : 7000L;
            }
        }
    }

    /**
     * Computes monthly driver perquisite value when employer provides driver.
     *
     * @param driverProvidedByEmployer Whether driver is provided by employer
     * @param policy                   Perquisite policy with driver rate
     * @return Monthly driver perquisite value
     */
    public long computeDriverPerquisiteMonthly(
            boolean driverProvidedByEmployer,
            PerquisitePolicyEntity policy
    ) {
        if (!driverProvidedByEmployer) {
            return 0L;
        }
        return policy.getDriverPerquisiteMonthly() != null
                ? policy.getDriverPerquisiteMonthly() : 3000L;
    }

    /**
     * Computes annual meal voucher exemption.
     *
     * @param actualMealVoucherAnnual Total meal voucher value received annually
     * @param policy                  Perquisite policy with meal rates
     * @return Annual exempt amount
     */
    public long computeMealVoucherExemptionAnnual(
            long actualMealVoucherAnnual,
            PerquisitePolicyEntity policy
    ) {
        long perMeal = policy.getMealPerMealAmount() != null ? policy.getMealPerMealAmount() : 200L;
        long mealsPerDay = policy.getMealMealsPerDay() != null ? policy.getMealMealsPerDay() : 2L;
        long workingDaysPerMonth = policy.getMealWorkingDaysPerMonth() != null
                ? policy.getMealWorkingDaysPerMonth() : 22L;

        long monthlyCap = perMeal * mealsPerDay * workingDaysPerMonth;
        long annualCap = monthlyCap * 12;

        return Math.min(actualMealVoucherAnnual, annualCap);
    }

    /**
     * Computes annual gift voucher exemption.
     *
     * @param actualGiftVoucherAnnual Total gift voucher value received annually
     * @param policy                  Perquisite policy with annual limit
     * @return Annual exempt amount
     */
    public long computeGiftVoucherExemptionAnnual(
            long actualGiftVoucherAnnual,
            PerquisitePolicyEntity policy
    ) {
        long annualLimit = policy.getGiftAnnualLimit() != null ? policy.getGiftAnnualLimit() : 15000L;
        return Math.min(actualGiftVoucherAnnual, annualLimit);
    }
}
```

## 9. `TaxComputationService` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/service/TaxComputationService.java` lines 22–101.

The orchestration and the regime comparison. `getComputation` (`:92-100`) has no caller.

```java
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
```

## 10. `RegimeAdvisor` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/recommendation/RegimeAdvisor.java` lines 12–88.

The second, independent regime comparison, used only by `RestructuringEngine` (`:164`).

```java
@Service
@Log4j2
@RequiredArgsConstructor
public class RegimeAdvisor {

    public RegimeAdvice advise(
            TaxComputationEntity.TaxResult newRegimeResult,
            TaxComputationEntity.TaxResult oldRegimeResult,
            SalaryProfileEntity profile
    ) {
        long newTax = newRegimeResult.getTotalTax();
        long oldTax = oldRegimeResult.getTotalTax();
        long savingIfNew = oldTax - newTax;

        RegimeType recommended = savingIfNew >= 0 ? RegimeType.NEW_REGIME : RegimeType.OLD_REGIME;
        long absoluteSaving = Math.abs(savingIfNew);

        List<String> switchTriggers = new ArrayList<>();

        if (recommended == RegimeType.NEW_REGIME) {
            if (profile.getInvestment80c() != null && profile.getInvestment80c() < 150000) {
                long unclaimed80c = 150000 - profile.getInvestment80c();
                long saving80c = Math.round(unclaimed80c * 0.30);
                switchTriggers.add("Maximise 80C by ₹" + unclaimed80c + " → saves ₹" + saving80c + " in old regime");
            }
            if (Boolean.TRUE.equals(profile.getIsPayingRent()) && profile.getMonthlyRentPaid() != null && profile.getMonthlyRentPaid() > 0) {
                switchTriggers.add("HRA exemption available in old regime if salary restructured");
            }
            if (profile.getHomeLoanInterest() != null && profile.getHomeLoanInterest() > 0) {
                switchTriggers.add("Home loan interest ₹" + profile.getHomeLoanInterest() + " deductible only in old regime");
            }
            if (profile.getInvestment80d() != null && profile.getInvestment80d() < 25000) {
                switchTriggers.add("Health insurance premium up to ₹25,000 under 80D — old regime only");
            }
            long breakeven = estimateBreakevenDeductions(profile, newTax, oldTax);
            long currentDeductions = currentTotalDeductions(profile);
            switchTriggers.add("Old regime needs ₹" + breakeven + " deductions to break even — you have ₹" + currentDeductions);
        } else {
            switchTriggers.add("New regime: simpler filing, no investment proofs needed");
            switchTriggers.add("New regime benefits: NPS Employer (80CCD(2)) + Meal Vouchers in both regimes");
        }

        return RegimeAdvice.builder()
                .recommendedRegime(recommended)
                .annualSaving(absoluteSaving)
                .newRegimeTotalTax(newTax)
                .oldRegimeTotalTax(oldTax)
                .switchTriggers(switchTriggers)
                .taxYear(profile.getTaxYear())
                .build();
    }

    private long estimateBreakevenDeductions(SalaryProfileEntity profile, long newTax, long oldTax) {
        if (oldTax <= newTax) {
            return 0;
        }
        long gap = oldTax - newTax;
        return Math.round(gap / 0.30);
    }

    private long currentTotalDeductions(SalaryProfileEntity profile) {
        long total = 0;
        if (profile.getInvestment80c() != null) {
            total += profile.getInvestment80c();
        }
        if (profile.getInvestment80d() != null) {
            total += profile.getInvestment80d();
        }
        if (profile.getNpsSelf80ccd1b() != null) {
            total += profile.getNpsSelf80ccd1b();
        }
        if (profile.getHomeLoanInterest() != null) {
            total += profile.getHomeLoanInterest();
        }
        return total;
    }
}
```

## 11. `RestructuringEngine` — complete class

`backend/src/main/java/com/thiru/wealthlens/taxplanning/recommendation/RestructuringEngine.java` lines 30–312.

The what-if engine: eligibility filter, priority order, suggested amounts, estimated saving, profile copy, and the second run of both engines.

```java
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
```

---

## 12. Form 16 parsing and TDS projection

**Neither exists.** There is no class, method, endpoint, entity, template or dependency in this
module — or anywhere else in `backend/src` — that parses a Form 16, models Section 192, projects
monthly TDS, or handles Form 12BB / Form 12B.

Evidence:

- `grep -rn -i "form16\|form 16\|form-16" backend/src --include="*.java"` → **0 hits**.
- `grep -rn -i "\btds\b\|section 192\|12BB\|payslip" backend/src --include="*.java"` → **0 hits**.
- The only occurrences anywhere in the repository are free-text strings inside
  `backend/src/main/resources/data/tax-policies/allowance-catalogue-2025-26.json` — user-facing
  guidance such as `"Form 16"` in a `documentsToKeep` array (lines 125, 165, 192, 1265, 1300, 1339,
  1379) and `"Automatically considered by employer in TDS"` in `howToAvailViaHr` (lines 1260, 1295,
  1330, 1409, 1448, 1487).
- No PDF **parsing** library is on the classpath for this module. The only PDF class imported is
  `org.xhtmlrenderer.pdf.ITextRenderer`
  (`backend/src/main/java/com/thiru/wealthlens/taxplanning/document/DocumentGenerationService.java:14`),
  which renders HTML **to** PDF.
- Apache POI is declared in `backend/pom.xml:60` but has no import under `taxplanning`.
- No `MultipartFile` import exists in the module, so there is no upload endpoint of any kind.

The closest artefact is `ParsedCtcDto`, an orphaned DTO with no producer and no consumer. It is
reproduced in full below because it is the clearest available statement of an intended — but
unbuilt — ingestion path, including its `confidence`, `unmappedComponents` and `parsingNotes`
fields.


`backend/src/main/java/com/thiru/wealthlens/taxplanning/parsing/ParsedCtcDto.java` lines 1–46, complete.

```java
package com.thiru.wealthlens.taxplanning.parsing;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ParsedCtcDto {

    private String employerName;
    private long totalCtcAnnual;
    private long basicAnnual;
    private long hraAnnual;
    private long ltaAnnual;
    private long specialAllowanceAnnual;
    private long mealVoucherAnnual;
    private long telephoneAnnual;
    private long fuelAllowanceAnnual;
    private long driverAllowanceAnnual;
    private long medicalReimbursementAnnual;
    private long booksPeriodicalsAnnual;
    private long uniformAllowanceAnnual;
    private long childrenEducationAnnual;
    private long hostelAllowanceAnnual;
    private long giftVoucherAnnual;
    private long npsEmployerAnnual;
    private long pfEmployerAnnual;
    private long gratuityAnnual;
    private long performanceBonusAnnual;
    private long variablePayAnnual;
    private long otherAllowancesAnnual;

    private String cityName;

    private boolean carProvided;
    private boolean carEngineAbove1600cc;
    private boolean driverProvided;

    private boolean esopPresent;
    private String esopDetails;

    private String confidence;
    private List<String> unmappedComponents;
    private String parsingNotes;
}
```
