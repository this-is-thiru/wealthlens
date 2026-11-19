package com.thiru.wealthlens.taxplanning.documents.reconcile;

import java.util.List;

/**
 * A year's confirmed certificates against one computation (M4.2): its lines and its verdict, and what they mean for the
 * user in words (W-132 C2a): a sentence for each reason on a line, in the lines' order, or why the year cannot be compared.
 */
public record ReconciliationReport(String computationId, List<String> documentIds, List<ReconciliationLine> lines, Verdict verdict,
        List<String> explanations) {

    public ReconciliationReport {
        if (computationId == null || documentIds == null || lines == null || verdict == null || explanations == null) {
            throw new IllegalArgumentException("A reconciliation report names its computation, certificates, lines, verdict and explanations");
        }
        documentIds = List.copyOf(documentIds);
        lines = List.copyOf(lines);
        explanations = List.copyOf(explanations);
    }
}
