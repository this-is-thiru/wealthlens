package com.thiru.wealthlens.taxengine.api;

/**
 * Whether each provident fund contribution of the residual's employment moves with basic pay ({@code 03-advisory.md}
 * A2.2 C8; W-238i). An employer that contributes on a capped wage does not change its contribution when basic changes,
 * and the user knows which applies; so the user states it, and no rate or wage ceiling is written in Java (CLAUDE.md §4).
 * A share that does not follow is held at the amount entered, whatever basic becomes.
 *
 * @param employerShareFollowsBasic whether the employer's recognised provident fund contribution follows basic at the
 *     ratio entered
 * @param employeeShareFollowsBasic whether {@code EMPLOYEE_PF} follows basic at the ratio entered
 */
public record ProvidentFundTerms(boolean employerShareFollowsBasic, boolean employeeShareFollowsBasic) {

    /** Both shares follow basic: what W-238d assumed for every employment, and the terms where none are stated. */
    public static final ProvidentFundTerms FOLLOW_BASIC = new ProvidentFundTerms(true, true);
}
