package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;

/**
 * One term on which money paid into a fund can come out (W-238e1), as the instrument that governs the fund states it
 * for a period. The terms are law outside the income-tax pack — the NPS exit regulations, the EPF scheme — and change
 * within a tax year, so each is dated and sourced, never a label derived from a component's kind (CLAUDE.md §4).
 *
 * @param event when the term applies, as the instrument names it (an exit at superannuation, a partial withdrawal)
 * @param terms what may then be withdrawn, in the instrument's figures
 * @param validity the days the instrument states it
 * @param provenance the Gazette copy and the provision it was read from
 */
public record WithdrawalTerm(String event, String terms, DateRange validity, Provenance provenance) {

    public WithdrawalTerm {
        if (event == null || event.isBlank() || terms == null || terms.isBlank()) {
            throw new IllegalArgumentException("A withdrawal term must name its event and its terms");
        }
        if (validity == null || provenance == null) {
            throw new IllegalArgumentException(event + " must say when it applies and where it was read");
        }
    }
}
