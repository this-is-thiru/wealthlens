package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A draft's dated actions for the year, each with what missing it costs (PRD F30; {@code DR 20} F30; W-114, D-665). A cost is
 * measured, not assumed: the draft computed with the action taken against the draft as it stands, both as of
 * {@code measuredAsOf}, counting only the change in interest and fees ({@code D-MARGINAL}). Entries are separate actions;
 * their costs overlap and are not to be added.
 *
 * @param asOf the day it was computed on; an entry dated before it has passed, and shows what was paid or furnished
 * @param measuredAsOf the later of the return's due date and {@code asOf}: interest is measured to it, the return taken as
 *     furnished on its due date unless the draft states when
 * @param regime the regime computed: the draft's election, or the year's default where none is made
 * @param revision the draft revision it read
 * @param entries in date order
 * @param notIncluded what no entry here covers yet
 */
public record DraftCalendar(LocalDate asOf, LocalDate measuredAsOf, String regime, int revision, List<Entry> entries,
        List<String> notIncluded) {

    public DraftCalendar {
        entries = List.copyOf(entries);
        notIncluded = List.copyOf(notIncluded);
    }

    /**
     * One dated action.
     *
     * @param on the date
     * @param kind what is due then
     * @param rule the row that dates it or charges for missing it
     * @param amountToPay what paying it in full takes; empty where nothing is to be paid
     * @param paidByThen advance tax paid by the date; empty for the return
     * @param furnishedOn for the return, the day it was furnished; empty otherwise, and where not yet furnished
     * @param costOfMissing the interest and fees missing it adds, or added; nil where nothing is left to miss
     */
    public record Entry(LocalDate on, Kind kind, String rule, Optional<Money> amountToPay, Optional<Money> paidByThen,
            Optional<LocalDate> furnishedOn, Money costOfMissing) {
    }

    /** The kinds of entry built so far (W-114 C1a); the rest of {@code DR 20} F30's table is W-114a and W-114b. */
    public enum Kind {
        /** An advance-tax instalment date (2025 s.408; 1961 s.211). */
        ADVANCE_TAX_INSTALMENT,
        /** The day by which tax on income arising after an instalment date must be paid to be relieved (s.425(4); s.234C). */
        DEFERMENT_RELIEF_PAID_BY,
        /** The return's due date (2025 s.263(1); 1961 s.139(1)), after which late-return interest and the fee run. */
        RETURN_DUE
    }
}
