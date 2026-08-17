package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.enums.CarEngineSize;
import com.thiru.wealthlens.taxplanning.enums.CarOwnership;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

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
