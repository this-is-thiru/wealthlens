package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An amount a certificate prints and the figure of the engine's computation it should equal (W-130a, D-755 C3a, D-756 C1a):
 * a node's output, or one of the computation's totals. Kept with the form's version, so a later form carries its own.
 *
 * @param printed the line the amount is on, or a table's Total cell, by its path in a reading
 * @param column the column of a line printing several cells, e.g. row 11's "Deductible Amount" (W-130b)
 * @param node the engine node whose output it should equal
 * @param total the computation's total it should equal, where no node holds it
 * @param reason the reconciliation reason that explains the pairing, e.g. {@code TAXSUM} for Part B against the bill
 * @param whenHigher the reason a certificate figure above the computation's has, e.g. a claim declared but not proven (W-130b)
 * @param whenLower the reason a certificate figure below the computation's has, e.g. a deduction never declared (W-130b)
 */
public record CertificateComparison(String printed, Optional<String> column, Optional<NodeId> node, Optional<ComparedTotal> total,
        Optional<String> reason, Optional<String> whenHigher, Optional<String> whenLower) {

    public CertificateComparison {
        if (printed == null || printed.isBlank() || column == null || node == null || total == null || reason == null
                || whenHigher == null || whenLower == null) {
            throw new IllegalArgumentException("A comparison names its amount, a node or a total, and its reasons or none");
        }
        if (Stream.of(column, reason, whenHigher, whenLower).anyMatch(text -> text.filter(String::isBlank).isPresent())) {
            throw new IllegalArgumentException(printed + "'s comparison names no blank column or reason");
        }
        if (node.isPresent() == total.isPresent()) {
            throw new IllegalArgumentException(printed + " is compared with a node or a total, exactly one");
        }
    }
}
