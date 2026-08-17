package com.thiru.wealthlens.taxplanning.engine;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

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
