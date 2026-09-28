package com.thiru.wealthlens.taxengine.input;

import java.util.List;
import java.util.Optional;

/**
 * One employment in the tax year, as entered ({@code DR 05} M3.2, D-090).
 *
 * <p>It holds only what a built rule reads: the period, previous-employer status and revisions each arrive with the
 * item that first reads them, so no field sits here unread. The employer type arrived with the entertainment
 * allowance (D-105), the company standing with the specified employee (D-113).
 *
 * @param components the pay lines, in the order entered
 * @param termsIncludeDearnessAllowance whether this employment's terms provide for dearness allowance to count
 *     in "salary" — the words of S4 r.279(2)(b), S2 s.19(2)(b), s.124(13)(b) and Sch. XV para 1(y), read by
 *     the salary bases (D-092). It is the employment's, not a pay line's, because the terms are
 * @param employerKind who pays the salary — 1961 s.16(ii) reads whether it is the Government
 * @param dearnessAllowanceCountsForRetirement whether dearness allowance enters the computation of this
 *     employment's superannuation or retirement benefits — the test S4 r.15(8)(k) and Rules 1962 r.3 Explanation
 *     (vi) apply for the accommodation perquisite's "salary" (D-111), which is not the terms test above
 * @param companyEmployer the employee's standing in the employer, where the employer is a company — read by the
 *     specified-employee test (D-113)
 */
public record Employment(
        List<SalaryComponent> components,
        boolean termsIncludeDearnessAllowance,
        EmployerKind employerKind,
        boolean dearnessAllowanceCountsForRetirement,
        Optional<CompanyEmployer> companyEmployer) {

    public Employment {
        if (components == null) {
            throw new IllegalArgumentException(
                    "An employment's components may not be null; pass an empty list where there are none");
        }
        if (employerKind == null) {
            throw new IllegalArgumentException("An employment must state its employer type");
        }
        if (companyEmployer == null) {
            throw new IllegalArgumentException("Pass Optional.empty() where the employer is not a company");
        }
        components = List.copyOf(components);
    }
}
