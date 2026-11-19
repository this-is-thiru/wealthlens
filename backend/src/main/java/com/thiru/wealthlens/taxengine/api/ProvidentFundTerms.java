package com.thiru.wealthlens.taxengine.api;

import java.util.Optional;

/**
 * How the provident fund contributions of the residual's employment move with basic pay ({@code 03-advisory.md} A2.2
 * C8; W-238i, W-238j). An employer that contributes on a capped wage does not change its contribution when basic
 * changes, and one may measure it on basic alone or on basic and dearness allowance; the user knows which applies, so
 * the user states it, and no rate or wage ceiling is written in Java (CLAUDE.md §4). A share that does not follow is
 * held at the amount entered, whatever basic becomes.
 *
 * @param employerShareFollowsBasic whether the employer's recognised provident fund contribution follows basic at the
 *     ratio entered
 * @param employeeShareFollowsBasic whether {@code EMPLOYEE_PF} follows basic at the ratio entered
 * @param baseIncludesDearnessAllowance whether both shares are measured on basic and dearness allowance rather than on
 *     basic alone; empty where not stated, and then an employment whose dearness allowance counts for retirement
 *     cannot move basic, since neither tax test of "salary" says what the employer pays on (W-238j C1a)
 */
public record ProvidentFundTerms(boolean employerShareFollowsBasic, boolean employeeShareFollowsBasic,
        Optional<Boolean> baseIncludesDearnessAllowance) {

    /** Both shares follow basic, the base not stated: what W-238d assumed, and the terms where none are stated. */
    public static final ProvidentFundTerms FOLLOW_BASIC = new ProvidentFundTerms(true, true, Optional.empty());

    public ProvidentFundTerms {
        if (baseIncludesDearnessAllowance == null) {
            throw new IllegalArgumentException("Pass Optional.empty() where the provident fund's base is not stated");
        }
    }
}
