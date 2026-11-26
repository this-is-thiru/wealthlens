package com.thiru.wealthlens.shared.migration;

import java.util.List;

/**
 * A source read and mapped, before anything is written (W-337g): every source is prepared, and checked against its target,
 * before the first row of any is copied, so a refused source stops the run with nothing written.
 *
 * @param children the child tables its lists fill (W-337g2a1)
 */
record PreparedCopy(String source, TargetTable table, List<ChildTable> children, List<SourceRow> rows) {

    PreparedCopy {
        children = List.copyOf(children);
        rows = List.copyOf(rows);
    }
}
