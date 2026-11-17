package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import java.util.Optional;

/**
 * An amount a certificate prints and the figure of the engine's computation it should equal (W-130a, D-755 C3a, D-756 C1a):
 * a node's output, or one of the computation's totals. Kept with the form's version, so a later form carries its own.
 *
 * @param printed the amount, by its path in a reading
 * @param node the engine node whose output it should equal
 * @param total the computation's total it should equal, where no node holds it
 * @param reason the reconciliation reason that explains the pairing, e.g. {@code TAXSUM} for Part B against the bill
 */
public record CertificateComparison(String printed, Optional<NodeId> node, Optional<ComparedTotal> total, Optional<String> reason) {

    public CertificateComparison {
        if (printed == null || printed.isBlank() || node == null || total == null || reason == null
                || reason.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("A comparison names its amount, a node or a total, and its reason or none");
        }
        if (node.isPresent() == total.isPresent()) {
            throw new IllegalArgumentException(printed + " is compared with a node or a total, exactly one");
        }
    }
}
