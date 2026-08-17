package com.thiru.wealthlens.taxplanning.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

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
