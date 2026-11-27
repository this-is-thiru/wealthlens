package com.thiru.wealthlens.shared.migration;

import java.util.List;

/**
 * A source read and mapped, before anything is written (W-337g): every source is prepared, and checked against its target,
 * before the first row of any is copied, so a refused source stops the run with nothing written.
 *
 * @param children the child tables its lists fill (W-337g2a1)
 * @param formerForms what was read in an earlier build's form, as {@link FormerForms#describe} names it (W-417)
 */
record PreparedCopy(String source, TargetTable table, List<ChildTable> children, List<SourceRow> rows, String formerForms) {

    PreparedCopy {
        children = List.copyOf(children);
        rows = List.copyOf(rows);
    }
}
