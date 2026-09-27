package com.thiru.wealthlens.taxengine.input;

import java.util.List;

/**
 * One employment in the tax year, as entered ({@code DR 05} M3.2, D-090).
 *
 * <p>It holds only what a built rule reads: the period, employer type, previous-employer status and revisions
 * each arrive with the item that first reads them, so no field sits here unread.
 *
 * @param components the pay lines, in the order entered
 * @param termsIncludeDearnessAllowance whether this employment's terms provide for dearness allowance to count
 *     in "salary" — the words of S4 r.279(2)(b), S2 s.19(2)(b), s.124(13)(b) and Sch. XV para 1(y), read by
 *     the salary bases (D-092). It is the employment's, not a pay line's, because the terms are
 */
public record Employment(List<SalaryComponent> components, boolean termsIncludeDearnessAllowance) {

    public Employment {
        if (components == null) {
            throw new IllegalArgumentException(
                    "An employment's components may not be null; pass an empty list where there are none");
        }
        components = List.copyOf(components);
    }
}
