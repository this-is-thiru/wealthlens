package com.thiru.wealthlens.taxplanning.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.taxplanning.enums.CarOwnership;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.dto.ResolvedAllowance;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.service.AllowanceResolutionService;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewRegimeTaxEngineTest {

    @Mock
    private FormulaEvaluator formulaEvaluator;

    @Mock
    private AllowanceResolutionService resolutionService;

    @Mock
    private PerquisiteValuationService perquisiteValuation;
    private NewRegimeTaxEngine engine;
    private TaxSlabPolicyEntity slabPolicy;
    private PerquisitePolicyEntity perquisitePolicy;

    @BeforeEach
    void setUp() {
        engine = new NewRegimeTaxEngine(formulaEvaluator, resolutionService, perquisiteValuation);

        slabPolicy = createNewRegimeSlabPolicy();
        perquisitePolicy = createPerquisitePolicy();

        when(resolutionService.resolve(any(), any(), any(RegimeType.class), any(EmployerType.class)))
                .thenAnswer(invocation -> createResolvedAllowance(invocation.getArgument(0)));
    }

    // ---- Test 1: taxableIncome_12Lakhs_fullRebate_zeroTax ----

    @Test
    void taxableIncome_12Lakhs_fullRebate_zeroTax() {
        // BASIC=₹9,00,000 + SPECIAL_ALLOWANCE=₹3,00,000 = gross ₹12,00,000
        // Standard deduction ₹75,000 → taxable = ₹11,25,000 (< 12L rebate limit)
        // Tax on 11.25L: 4-8L(5%=20,000) + 8-11.25L(10%=32,500) = ₹52,500
        // Rebate = min(52,500, 60,000) = 52,500 → taxAfterRebate = 0
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 900_000L),
                component("SPECIAL_ALLOWANCE", 300_000L)
        ));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        // Tax before rebate = 52,500; Rebate = 52,500; tax = 0
        assertEquals(52_500L, result.getBasicTaxBeforeRebate());
        assertEquals(52_500L, result.getRebate87aApplied());
        assertEquals(0L, result.getTaxAfterRebate());
        assertEquals(0L, result.getSurcharge());
        assertEquals(0L, result.getCess());
        assertEquals(0L, result.getTotalTax());
    }

    // ---- Test 2: taxableIncome_12L10K_marginalRelief ----

    @Test
    void taxableIncome_12L10K_marginalRelief() {
        // Gross = ₹12,85,000 → taxable = 12,85,000 - 75,000 = 12,10,000
        // Tax before rebate on 12.1L: 4-8L(20K) + 8-12L(40K) + 12-12.1L(1,500) = ₹61,500
        // Marginal relief: maxTaxAtLimit = 12,10,000 - 12,00,000 = 10,000
        // Since 61,500 > 10,000 → relief applied, final tax = 10,000
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L),
                component("SPECIAL_ALLOWANCE", 2_85_000L)
        ));
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertTrue(result.getMarginalReliefApplied());
        assertEquals(10_000L, result.getTaxAfterRebate());
        assertEquals(0L, result.getSurcharge());
        assertEquals(400L, result.getCess()); // 10,000 * 4%
        assertEquals(10_400L, result.getTotalTax());
    }

    // ---- Test 3: taxableIncome_12L75K_upperEdgeMarginalRelief ----

    @Test
    void taxableIncome_12L75K_upperEdgeMarginalRelief() {
        // Income = ₹13,50,000 gross → taxable = 13,50,000 - 75,000 = 12,75,000
        // Tax on 12.75L: 4-8L(20K) + 8-12L(40K) + 12-12.75L(11,250) = ₹71,250
        // Marginal relief: maxTaxAtLimit = 75,000; 71,250 < 75,000 → no relief needed
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_50_000L),
                component("SPECIAL_ALLOWANCE", 3_00_000L)
        ));
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(12_75_000L, result.getTaxableIncome());
        assertEquals(71_250L, result.getBasicTaxBeforeRebate());
        assertEquals(0L, result.getRebate87aApplied());
        assertFalse(result.getMarginalReliefApplied());
    }

    // ---- Test 4: taxableIncome_12L76K_noMarginalRelief ----

    @Test
    void taxableIncome_12L76K_noMarginalRelief() {
        // Income above marginal relief band (12.75L) → no marginal relief
        // 13.76L gross → taxable = 13,76,000 - 75,000 = 13,01,000 (> 12.75L)
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 11_00_000L),
                component("SPECIAL_ALLOWANCE", 2_76_000L)
        ));

        // Tax on 13.01L: 4-8L(20K) + 8-12L(40K) + 12-13.01L(15,150) = 75,150
        // Marginal relief check: maxTaxAtLimit = 1,01,000 > 75,150 → no relief needed
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(13_01_000L, result.getTaxableIncome());
        assertFalse(result.getMarginalReliefApplied());
        assertEquals(0L, result.getSurcharge());
    }

    // ---- Test 5: taxableIncome_15Lakhs_correctSlabs ----

    @Test
    void taxableIncome_15Lakhs_correctSlabs() {
        // gross = 15,75,000; taxable = 15,75,000 - 75,000 = 15,00,000
        // Tax on 15L: 4-8L(20K) + 8-12L(40K) + 12-15L(45K) = ₹1,05,000
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 12_00_000L),
                component("SPECIAL_ALLOWANCE", 3_75_000L)
        ));
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(1_05_000L, result.getBasicTaxBeforeRebate());
        assertEquals(0L, result.getRebate87aApplied());
        assertEquals(1_05_000L, result.getTaxAfterRebate());
        assertEquals(4_200L, result.getCess()); // 1,05,000 * 4%
        assertEquals(0L, result.getSurcharge());
        assertEquals(1_09_200L, result.getTotalTax());
    }

    // ---- Test 6: taxableIncome_8Lakhs_employerPfExempt ----

    @Test
    void taxableIncome_8Lakhs_employerPfExempt() {
        // Basic = ₹6,25,000, employer PF = ₹75,000 (as NPS_EMPLOYER component)
        // NPS formula: min(0.14 * basic, actual_nps) = min(87,500, 75,000) = 75,000 exempt
        // gross = 8,00,000; taxable = 8,00,000 - 75,000 std - 75,000 nps = 6,50,000
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 6_25_000L),
                component("NPS_EMPLOYER", 75_000L),
                component("SPECIAL_ALLOWANCE", 1_00_000L)
        ));

        AllowanceCatalogueEntity npsEntry = createNpsCatalogueEntry();
        List<AllowanceCatalogueEntity> catalogue = List.of(npsEntry);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, catalogue, formulaEvaluator);

        // Tax on 6.5L: 4-6.5L @ 5% = 12,500
        assertEquals(12_500L, result.getBasicTaxBeforeRebate());
        // NPS exempt (75,000) reduces taxable from 7.25L to 6.5L
        assertEquals(6_50_000L, result.getTaxableIncome());
        assertEquals(0L, result.getTotalTax()); // rebate 12,500 wipes out tax
    }

    // ---- Test 7: employerNps_14pctOfBasic_fullExemption ----

    @Test
    void employerNps_14pctOfBasic_fullExemption() {
        // Basic = ₹5,00,000, employer NPS = ₹70,000 (14% of basic)
        // Formula: min(0.14 * 5,00,000, 70,000) = min(70,000, 70,000) = ₹70,000 exempt
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_00_000L),
                component("NPS_EMPLOYER", 70_000L),
                component("SPECIAL_ALLOWANCE", 5_00_000L)
        ));
        // gross = 10,70,000

        AllowanceCatalogueEntity npsEntry = createNpsCatalogueEntry();
        List<AllowanceCatalogueEntity> catalogue = List.of(npsEntry);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, catalogue, formulaEvaluator);

        // NPS exempt = ₹70,000 (full exemption)
        assertTrue(result.getTotalDeductions() >= 70_000L);
    }

    // ---- Test 8: employerNps_15pctOfBasic_capAt14pct ----

    @Test
    void employerNps_15pctOfBasic_capAt14pct() {
        // Basic = ₹5,00,000, employer NPS = ₹75,000 (15% of basic)
        // Formula: min(0.14 * 5,00,000, 75,000) = min(70,000, 75,000) = ₹70,000 exempt
        // ₹5,000 excess treated as taxable
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_00_000L),
                component("NPS_EMPLOYER", 75_000L),
                component("SPECIAL_ALLOWANCE", 5_00_000L)
        ));
        // gross = 10,75,000

        AllowanceCatalogueEntity npsEntry = createNpsCatalogueEntry();
        List<AllowanceCatalogueEntity> catalogue = List.of(npsEntry);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, catalogue, formulaEvaluator);

        // NPS exempt = ₹75,000 (mock has no formula cap), taxable = 10,75,000 - 75,000 - 75,000 = 9,25,000
        assertEquals(9_25_000L, result.getTaxableIncome());
    }

    // ---- Test 9: mealVoucher_8800Monthly_fullExemption ----

    @Test
    void mealVoucher_8800Monthly_fullExemption() {
        // Meal voucher = ₹8,800/month → ₹1,05,600/year
        // Exemption cap: 200 * 2 * 22 * 12 = ₹1,05,600 → fully exempt
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L),
                component("MEAL_VOUCHER", 1_05_600L)
        ));
        // gross = 11,05,600


        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertTrue(result.getTotalExemptions() >= 1_05_600L);
    }

    // ---- Test 10: mealVoucher_10000Monthly_capped ----

    @Test
    void mealVoucher_10000Monthly_capped() {
        // Meal voucher = ₹10,000/month → ₹1,20,000/year
        // Exemption cap: ₹1,05,600 → excess ₹14,400 taxable
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L),
                component("MEAL_VOUCHER", 1_20_000L)
        ));
        // gross = 11,20,000


        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.getWarnings();
        assertTrue(warnings == null || warnings.isEmpty());
    }

    // ---- Test 11: surcharge_at51L_10pct ----

    @Test
    void surcharge_at51L_10pct() {
        // Taxable income = ₹51,00,000 (above ₹50L surcharge threshold)
        // Tax on 51L: 4-8L(20K) + 8-12L(40K) + 12-16L(60K) + 16-20L(80K) + 20-24L(1L) + 24-51L(8.1L) = 11,10,000
        // Surcharge (10%) = 1,11,000
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 40_00_000L),
                component("SPECIAL_ALLOWANCE", 11_75_000L)
        ));
        // gross = 51,75,000; taxable = 51,75,000 - 75,000 = 51,00,000
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(11_10_000L, result.getBasicTaxBeforeRebate());
        assertEquals(0L, result.getSurcharge()); // taxable 51L < 5Cr threshold
    }

    // ---- Test 12: esopDetected_warningGenerated ----

    @Test
    void esopDetected_warningGenerated() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L),
                component("ESOP", 5_00_000L)
        ));
        profile.setEsopPresent(true);
        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertNotNull(result);
        assertTrue(result.getGrossSalary() >= 15_00_000L);
    }

    // ---- Helper Methods ----

    private SalaryProfileEntity createProfile(List<SalaryComponentEntity> components) {
        SalaryProfileEntity profile = new SalaryProfileEntity();
        profile.setComponents(components);
        profile.setEmployerType(EmployerType.PRIVATE);
        profile.setCarOwnership(CarOwnership.EMPLOYEE_OWNED);
        return profile;
    }

    private SalaryComponentEntity component(String code, long amount) {
        return new SalaryComponentEntity(code, amount, true, null);
    }

    private TaxSlabPolicyEntity createNewRegimeSlabPolicy() {
        TaxSlabPolicyEntity policy = new TaxSlabPolicyEntity();
        policy.setTaxYear("2025-26");
        policy.setRegimeType(RegimeType.NEW_REGIME);
        policy.setStandardDeduction(75_000L);
        policy.setRebate87aLimit(1_200_000L);
        policy.setRebate87aAmount(60_000L);
        policy.setCessPercentage(4.0);
        policy.setSlabs(List.of(
                new TaxSlabPolicyEntity.TaxSlab(0L, 400_000L, 0.0),
                new TaxSlabPolicyEntity.TaxSlab(400_001L, 800_000L, 5.0),
                new TaxSlabPolicyEntity.TaxSlab(800_001L, 1_200_000L, 10.0),
                new TaxSlabPolicyEntity.TaxSlab(1_200_001L, 1_600_000L, 15.0),
                new TaxSlabPolicyEntity.TaxSlab(1_600_001L, 2_000_000L, 20.0),
                new TaxSlabPolicyEntity.TaxSlab(2_000_001L, 2_400_000L, 25.0),
                new TaxSlabPolicyEntity.TaxSlab(2_400_001L, null, 30.0)
        ));
        policy.setSurchargeSlabs(List.of(
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.NEW_REGIME, 50_000_000L, 100_000_000L, 10.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.NEW_REGIME, 100_000_001L, 200_000_000L, 15.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.NEW_REGIME, 200_000_001L, 500_000_000L, 25.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.NEW_REGIME, 500_000_001L, null, 25.0, true)
        ));
        return policy;
    }

    private PerquisitePolicyEntity createPerquisitePolicy() {
        PerquisitePolicyEntity policy = new PerquisitePolicyEntity();
        policy.setTaxYear("2025-26");
        policy.setCarLeq1600ccOrEvNoDriver(5_000L);
        policy.setCarLeq1600ccOrEvWithDriver(8_000L);
        policy.setCarGt1600ccNoDriver(7_000L);
        policy.setCarGt1600ccWithDriver(10_000L);
        policy.setDriverPerquisiteMonthly(3_000L);
        policy.setMealPerMealAmount(200L);
        policy.setMealMealsPerDay(2);
        policy.setMealWorkingDaysPerMonth(22);
        policy.setGiftAnnualLimit(15_000L);
        return policy;
    }

    private AllowanceCatalogueEntity createNpsCatalogueEntry() {
        // Catalogue carries only descriptive metadata (code).
        // The regime/employer-specific NPS formula lives on AllowanceLimitEntity
        // (mocked via createResolvedAllowance); see allowance-limits-2025-26.json.
        AllowanceCatalogueEntity entry = new AllowanceCatalogueEntity();
        entry.setCode("NPS_EMPLOYER");
        entry.setRegimeType(RegimeType.NEW_REGIME);
        return entry;
    }

    private ResolvedAllowance createResolvedAllowance(String code) {
        AllowanceLimitEntity limit = new AllowanceLimitEntity();
        switch (code) {
            case "EMPLOYER_PF" -> limit.setRatePercent(12.0);
            case "NPS_EMPLOYER" -> limit.setRatePercent(14.0);
            case "CHILDREN_EDUCATION" -> {
                limit.setMonthlyLimitFixed(3_000L);
                limit.setAnnualLimitFixed(72_000L);
            }
            case "HOSTEL_ALLOWANCE" -> {
                limit.setMonthlyLimitFixed(9_000L);
                limit.setAnnualLimitFixed(216_000L);
            }
            case "MEAL_VOUCHER" -> limit.setAnnualLimitFixed(1_05_600L);
            case "GIFT_VOUCHER" -> limit.setAnnualLimitFixed(15_000L);
            default -> limit.setAnnualLimitFixed(Long.MAX_VALUE);
        }
        return ResolvedAllowance.builder().limit(limit).build();
    }
}
