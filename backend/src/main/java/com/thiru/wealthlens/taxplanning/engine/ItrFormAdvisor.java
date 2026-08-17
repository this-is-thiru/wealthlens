package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class ItrFormAdvisor {

    private static final long SALARY_THRESHOLD_FOR_ITR2 = 5000000L; // ₹50 Lakhs

    /**
     * Recommends the appropriate ITR form based on salary profile and capital gains.
     * ITR-1: For individuals having salary income only, up to ₹50L, no capital gains
     * ITR-2: For individuals with capital gains OR gross salary > ₹50L
     */
    public String recommendItrForm(SalaryProfileEntity profile, boolean hasCapitalGains) {
        long grossSalary = getGrossSalary(profile);

        if (hasCapitalGains || grossSalary > SALARY_THRESHOLD_FOR_ITR2) {
            log.debug("ITR-2 recommended: hasCapitalGains={} grossSalary={}", hasCapitalGains, grossSalary);
            return "ITR-2";
        }

        return "ITR-1";
    }

    /**
     * Returns the total gross salary from profile components.
     */
    long getGrossSalary(SalaryProfileEntity profile) {
        if (profile.getComponents() == null || profile.getComponents().isEmpty()) {
            return 0L;
        }
        return profile.getComponents().stream()
                .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                .sum();
    }
}
