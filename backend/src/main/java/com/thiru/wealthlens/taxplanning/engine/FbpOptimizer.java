package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Flexible Benefits Plan (FBP) optimizer helper.
 * Provides utilities to extract salary components by code and compute
 * the reducible/available pool for tax-efficient restructuring.
 */
@Component
public class FbpOptimizer {

    /**
     * Returns the SPECIAL_ALLOWANCE annual amount — this is the primary pool
     * from which restructuring can fund other exempt components.
     */
    public long getReducibleSpecialAllowance(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "SPECIAL_ALLOWANCE");
    }

    /**
     * Returns the BASIC salary annual amount.
     */
    public long getBasicSalaryAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "BASIC");
    }

    /**
     * Returns the DA (Dearness Allowance) annual amount.
     */
    public long getDaAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "DA");
    }

    /**
     * Returns the HRA annual amount from components.
     */
    public long getHraAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "HRA");
    }

    /**
     * Returns the LTA annual amount from components.
     */
    public long getLtaAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "LTA");
    }

    /**
     * Returns the meal voucher annual amount from components.
     */
    public long getMealVoucherAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "MEAL_VOUCHER");
    }

    /**
     * Returns the gift voucher annual amount from components.
     */
    public long getGiftVoucherAnnual(List<SalaryComponentEntity> components) {
        return getComponentAnnual(components, "GIFT_VOUCHER");
    }

    /**
     * Finds a salary component by its allowance code.
     */
    public Optional<SalaryComponentEntity> findComponent(List<SalaryComponentEntity> components, String code) {
        if (components == null) {
            return Optional.empty();
        }
        return components.stream()
                .filter(c -> code.equals(c.getAllowanceCode()))
                .findFirst();
    }

    /**
     * Returns the annual amount for a given allowance code, or 0 if not found.
     */
    public long getComponentAnnual(List<SalaryComponentEntity> components, String code) {
        return findComponent(components, code)
                .map(SalaryComponentEntity::getAnnualAmount)
                .orElse(0L);
    }
}
