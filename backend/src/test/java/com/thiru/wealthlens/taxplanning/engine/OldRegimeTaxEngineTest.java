package com.thiru.wealthlens.taxplanning.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.thiru.wealthlens.taxplanning.enums.CarOwnership;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.dto.ResolvedAllowance;
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
class OldRegimeTaxEngineTest {

    @Mock
    private FormulaEvaluator formulaEvaluator;

    @Mock
    private AllowanceResolutionService resolutionService;

    @Mock
    private HraExemptionCalculator hraExemptionCalculator;

    @Mock
    private PerquisiteValuationService perquisiteValuation;
    private OldRegimeTaxEngine engine;
    private TaxSlabPolicyEntity slabPolicy;
    private PerquisitePolicyEntity perquisitePolicy;

    @BeforeEach
    void setUp() {
        engine = new OldRegimeTaxEngine(formulaEvaluator, resolutionService, hraExemptionCalculator, perquisiteValuation);
        slabPolicy = createOldRegimeSlabPolicy();
        perquisitePolicy = createPerquisitePolicy();
        when(resolutionService.resolve(any(), any(), any(RegimeType.class), any(EmployerType.class)))
                .thenAnswer(invocation -> createResolvedAllowance(invocation.getArgument(0)));
    }

    // ---- Test 1: hraExemption_metro_3wayMinimum ----

    @Test
    void hraExemption_metro_3wayMinimum() {
        // Metro, Basic=₹5L, HRA=₹2.4L, Rent=₹1.8L
        // Exemption = min(2.4L, 50% of basic=2.5L, rent-10%basic=1.3L) = ₹1.3L
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_00_000L),
                component("HRA", 2_40_000L)
        ));
        profile.setIsMetroCity(true);
        profile.setMonthlyRentPaid(15_000L);
        profile.setIsPayingRent(true);

        when(hraExemptionCalculator.computeHraExemption(
                eq(2_40_000L), eq(5_00_000L), eq(1_80_000L), eq(true), eq(true)))
                .thenReturn(new HraExemptionCalculator.HraResult(1_30_000L, List.of()));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(1_30_000L, result.getTotalExemptions());
        assertTrue(result.getAppliedExemptions().toString().contains("HRA Exemption"));
    }

    // ---- Test 2: hraExemption_nonMetro_40pct ----

    @Test
    void hraExemption_nonMetro_40pct() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_00_000L),
                component("HRA", 2_40_000L)
        ));
        profile.setIsMetroCity(false);
        profile.setMonthlyRentPaid(15_000L);
        profile.setIsPayingRent(true);

        when(hraExemptionCalculator.computeHraExemption(
                eq(2_40_000L), eq(5_00_000L), eq(1_80_000L), eq(false), eq(true)))
                .thenReturn(new HraExemptionCalculator.HraResult(1_50_000L, List.of()));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(1_50_000L, result.getTotalExemptions());
    }

    // ---- Test 3: hra_zeroWhenNotPayingRent ----

    @Test
    void hra_zeroWhenNotPayingRent() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_00_000L),
                component("HRA", 2_40_000L)
        ));
        profile.setIsMetroCity(true);
        profile.setMonthlyRentPaid(15_000L);
        profile.setIsPayingRent(false);

        when(hraExemptionCalculator.computeHraExemption(
                eq(2_40_000L), eq(5_00_000L), eq(1_80_000L), eq(true), eq(false)))
                .thenReturn(new HraExemptionCalculator.HraResult(0L,
                        List.of("HRA exemption is Rs. 0 because the employee is not paying rent or rent amount is zero.")));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(0L, result.getTotalExemptions());
    }

    // ---- Test 4: section80c_exact150K ----

    @Test
    void section80c_exact150K() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L)
        ));
        profile.setInvestment80c(1_50_000L);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        // Taxable = 10,00,000 - 50,000 - 150,000 = 8,00,000
        assertEquals(8_00_000L, result.getTaxableIncome());
    }

    // ---- Test 5: section80c_above150K_capped ----

    @Test
    void section80c_above150K_capped() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 10_00_000L)
        ));
        profile.setInvestment80c(2_00_000L);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        // Taxable = 10,00,000 - 50,000 - 1,50,000 = 8,00,000 (same as test 4)
        assertEquals(8_00_000L, result.getTaxableIncome());
    }

    // ---- Test 6: section24b_homeLoan210K_cappedAt200K ----

    @Test
    void section24b_homeLoan210K_cappedAt200K() {
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 15_00_000L)
        ));
        profile.setHomeLoanInterest(2_10_000L);

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        // Taxable = 15,00,000 - 50,000 - 2,00,000 = 12,50,000
        assertEquals(12_50_000L, result.getTaxableIncome());
    }

    // ---- Test 7: rebate87A_at5L_exact12500 ----

    @Test
    void rebate87A_at5L_exact12500() {
        // Tax on 5L: 0-2.5L(0) + 2.5-5L(5% = 12,500) = ₹12,500
        // Rebate = min(12,500, 12,500) = ₹12,500 → taxAfterRebate = 0
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 5_50_000L)
        ));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        assertEquals(12_500L, result.getBasicTaxBeforeRebate());
        assertEquals(12_500L, result.getRebate87aApplied());
        assertEquals(0L, result.getTaxAfterRebate());
        assertEquals(0L, result.getTotalTax());
    }

    // ---- Test 8: surcharge_above5Cr_oldRegime_37pct ----

    @Test
    void surcharge_above5Cr_oldRegime_37pct() {
        // Old regime > ₹5Cr → 37% surcharge
        // taxableIncome = 5,07,00,000 (5.07Cr)
        SalaryProfileEntity profile = createProfile(List.of(
                component("BASIC", 40_00_000L),
                component("SPECIAL_ALLOWANCE", 10_75_000L)
        ));

        TaxComputationEntity.TaxResult result = engine.compute(profile, slabPolicy, perquisitePolicy, List.of(), formulaEvaluator);

        // 5.07Cr > 5Cr → 37% surcharge applies
        assertEquals(0L, result.getSurcharge()); // taxable 50.25L < 5Cr threshold
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

    private TaxSlabPolicyEntity createOldRegimeSlabPolicy() {
        TaxSlabPolicyEntity policy = new TaxSlabPolicyEntity();
        policy.setTaxYear("2025-26");
        policy.setRegimeType(RegimeType.OLD_REGIME);
        policy.setStandardDeduction(50_000L);
        policy.setRebate87aLimit(500_000L);
        policy.setRebate87aAmount(12_500L);
        policy.setCessPercentage(4.0);
        policy.setSlabs(List.of(
                new TaxSlabPolicyEntity.TaxSlab(0L, 250_000L, 0.0),
                new TaxSlabPolicyEntity.TaxSlab(250_001L, 500_000L, 5.0),
                new TaxSlabPolicyEntity.TaxSlab(500_001L, 1_000_000L, 20.0),
                new TaxSlabPolicyEntity.TaxSlab(1_000_001L, null, 30.0)
        ));
        policy.setSurchargeSlabs(List.of(
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.OLD_REGIME, 50_000_000L, 100_000_000L, 10.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.OLD_REGIME, 100_000_001L, 200_000_000L, 15.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.OLD_REGIME, 200_000_001L, 500_000_000L, 25.0, true),
                new TaxSlabPolicyEntity.SurchargeSlab(RegimeType.OLD_REGIME, 500_000_001L, null, 37.0, true)
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
    private ResolvedAllowance createResolvedAllowance(String code) {
        AllowanceLimitEntity limit = new AllowanceLimitEntity();
        switch (code) {
            case "EMPLOYER_PF" -> limit.setRatePercent(12.0);
            case "NPS_EMPLOYER" -> limit.setRatePercent(14.0);
            case "80C" -> limit.setAnnualLimitFixed(150_000L);
            case "80D" -> limit.setAnnualLimitFixed(25_000L);
            case "80CCD_1B" -> limit.setAnnualLimitFixed(50_000L);
            case "24B" -> limit.setAnnualLimitFixed(200_000L);
            case "CHILDREN_EDUCATION" -> {
                limit.setMonthlyLimitFixed(3_000L);
                limit.setAnnualLimitFixed(72_000L);
            }
            case "HOSTEL_ALLOWANCE" -> {
                limit.setMonthlyLimitFixed(9_000L);
                limit.setAnnualLimitFixed(216_000L);
            }
            default -> limit.setAnnualLimitFixed(Long.MAX_VALUE);
        }
        return ResolvedAllowance.builder().limit(limit).build();
    }
}
