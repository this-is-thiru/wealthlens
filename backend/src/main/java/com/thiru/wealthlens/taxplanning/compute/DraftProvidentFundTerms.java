package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ProvidentFundTerms;
import java.util.Optional;

/**
 * How the request says the provident fund moves with basic (W-238i, W-238j; {@code 03-advisory.md} A2.2 C8), each
 * field optional so a request states only what differs from the engine's {@link ProvidentFundTerms#FOLLOW_BASIC}.
 *
 * @param employerShareFollowsBasic whether the employer's contribution moves with basic; it does when absent
 * @param employeeShareFollowsBasic whether {@code EMPLOYEE_PF} moves with basic; it does when absent
 * @param baseIncludesDearnessAllowance whether both shares are measured on basic and dearness allowance; not stated
 *     when absent
 */
public record DraftProvidentFundTerms(Optional<Boolean> employerShareFollowsBasic,
        Optional<Boolean> employeeShareFollowsBasic, Optional<Boolean> baseIncludesDearnessAllowance) {

    public DraftProvidentFundTerms {
        employerShareFollowsBasic = employerShareFollowsBasic == null ? Optional.empty() : employerShareFollowsBasic;
        employeeShareFollowsBasic = employeeShareFollowsBasic == null ? Optional.empty() : employeeShareFollowsBasic;
        baseIncludesDearnessAllowance = baseIncludesDearnessAllowance == null ? Optional.empty()
                : baseIncludesDearnessAllowance;
    }

    ProvidentFundTerms toTerms() {
        return new ProvidentFundTerms(employerShareFollowsBasic.orElse(true), employeeShareFollowsBasic.orElse(true),
                baseIncludesDearnessAllowance);
    }
}
