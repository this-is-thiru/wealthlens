package com.thiru.wealthlens.taxplanning.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.taxplanning.enums.CarEngineSize;
import com.thiru.wealthlens.taxplanning.enums.CarOwnership;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerquisiteValuationServiceTest {

    private PerquisiteValuationService service;
    private PerquisitePolicyEntity policy;

    @BeforeEach
    void setUp() {
        service = new PerquisiteValuationService();
        policy = createPerquisitePolicy();
    }

    // ---- Test 1: carLeq1600cc_noDriver_5000 ----

    @Test
    void carLeq1600cc_noDriver_5000() {
        long monthly = service.computeCarPerquisiteMonthly(
                CarOwnership.EMPLOYER_OWNED,
                CarEngineSize.LEQ_1600CC_OR_EV,
                false,  // no driver
                true,   // partly personal
                policy
        );

        // Policy: carLeq1600ccOrEvNoDriver = 5,000/month
        assertEquals(5_000L, monthly);
    }

    // ---- Test 2: carLeq1600cc_withDriver_8000 ----

    @Test
    void carLeq1600cc_withDriver_8000() {
        long monthly = service.computeCarPerquisiteMonthly(
                CarOwnership.EMPLOYER_OWNED,
                CarEngineSize.LEQ_1600CC_OR_EV,
                true,   // with driver
                true,   // partly personal
                policy
        );

        // Policy: carLeq1600ccOrEvWithDriver = 8,000/month (5,000 + 3,000)
        assertEquals(8_000L, monthly);
    }

    // ---- Test 3: carGt1600cc_noDriver_7000 ----

    @Test
    void carGt1600cc_noDriver_7000() {
        long monthly = service.computeCarPerquisiteMonthly(
                CarOwnership.EMPLOYER_OWNED,
                CarEngineSize.GT_1600CC,
                false,  // no driver
                true,   // partly personal
                policy
        );

        // Policy: carGt1600ccNoDriver = 7,000/month
        assertEquals(7_000L, monthly);
    }

    // ---- Test 4: carOnlyOfficialUse_zero ----

    @Test
    void carOnlyOfficialUse_zero() {
        long monthly = service.computeCarPerquisiteMonthly(
                CarOwnership.EMPLOYER_OWNED,
                CarEngineSize.LEQ_1600CC_OR_EV,
                false,  // no driver
                false,  // NOT partly personal — only official use
                policy
        );

        // No personal use → perquisite = 0
        assertEquals(0L, monthly);
    }

    // ---- Test 5: carEmployeeOwned_zero ----

    @Test
    void carEmployeeOwned_zero() {
        long monthly = service.computeCarPerquisiteMonthly(
                CarOwnership.EMPLOYEE_OWNED,  // employee owns the car
                CarEngineSize.GT_1600CC,
                true,
                true,
                policy
        );

        // Employee-owned car → no perquisite
        assertEquals(0L, monthly);
    }

    // ---- Test 6: driverOnly_noCar ----

    @Test
    void driverOnly_noCar() {
        long monthly = service.computeDriverPerquisiteMonthly(
                true,   // driver provided by employer
                policy
        );

        // Driver perquisite = policy driver value = 3,000/month
        assertEquals(3_000L, monthly);
    }

    // ---- Test 7: noDriver_zeroDriverPerquisite ----

    @Test
    void noDriver_zeroDriverPerquisite() {
        long monthly = service.computeDriverPerquisiteMonthly(
                false,  // no driver
                policy
        );

        assertEquals(0L, monthly);
    }

    // ---- Test 8: mealVoucher_withinLimit_fullExemption ----

    @Test
    void mealVoucher_withinLimit_fullExemption() {
        // Annual meal voucher = 1,00,000 (within ₹1,05,600 cap)
        long exempt = service.computeMealVoucherExemptionAnnual(1_00_000L, policy);

        // Cap = 200 * 2 * 22 * 12 = 1,05,600. Actual (1,00,000) < cap → full exempt
        assertEquals(1_00_000L, exempt);
    }

    // ---- Test 9: mealVoucher_exceedsLimit_capped ----

    @Test
    void mealVoucher_exceedsLimit_capped() {
        // Annual meal voucher = 1,20,000 (exceeds ₹1,05,600 cap)
        long exempt = service.computeMealVoucherExemptionAnnual(1_20_000L, policy);

        // Capped at 1,05,600
        assertEquals(1_05_600L, exempt);
    }

    // ---- Test 10: giftVoucher_withinLimit_fullExemption ----

    @Test
    void giftVoucher_withinLimit_fullExemption() {
        // Annual gift = 10,000 (within 15,000 limit)
        long exempt = service.computeGiftVoucherExemptionAnnual(10_000L, policy);

        assertEquals(10_000L, exempt);
    }

    // ---- Helper Methods ----

    private PerquisitePolicyEntity createPerquisitePolicy() {
        PerquisitePolicyEntity p = new PerquisitePolicyEntity();
        p.setTaxYear("2025-26");
        p.setCarLeq1600ccOrEvNoDriver(5_000L);
        p.setCarLeq1600ccOrEvWithDriver(8_000L);
        p.setCarGt1600ccNoDriver(7_000L);
        p.setCarGt1600ccWithDriver(10_000L);
        p.setDriverPerquisiteMonthly(3_000L);
        p.setMealPerMealAmount(200L);
        p.setMealMealsPerDay(2);
        p.setMealWorkingDaysPerMonth(22);
        p.setGiftAnnualLimit(15_000L);
        return p;
    }
}
