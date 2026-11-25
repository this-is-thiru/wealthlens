package com.thiru.wealthlens.shared.migration;

import java.util.List;

/**
 * A source read and mapped, before anything is written (W-337g): every source is prepared, and checked against its target,
 * before the first row of any is copied, so a refused source stops the run with nothing written.
 */
record PreparedCopy(String source, TargetTable table, List<SourceRow> rows) {

    PreparedCopy {
        rows = List.copyOf(rows);
    }
}
