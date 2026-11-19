package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.List;

/**
 * The row a certificate's table prints its totals in (W-126c2a, D-737 C2a): its printed label, and its cells in printed
 * order, each naming the column keys it spans — one, or several for a merged cell such as Part B's "Amount
 * paid/credited" and "Rate of deduction of tax".
 */
public record TableTotal(String label, List<List<String>> cells) {

    public TableTotal {
        if (label == null || label.isBlank() || cells == null || cells.isEmpty() || cells.stream().anyMatch(cell -> cell == null || cell.isEmpty())) {
            throw new IllegalArgumentException("A total row needs its printed label and at least one cell, each spanning a column");
        }
        cells = cells.stream().map(List::copyOf).toList();
    }
}
