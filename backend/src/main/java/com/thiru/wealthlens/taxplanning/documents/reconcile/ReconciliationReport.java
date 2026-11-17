package com.thiru.wealthlens.taxplanning.documents.reconcile;

import java.util.List;

/** A year's confirmed certificates against one computation (M4.2): its lines and its verdict. */
public record ReconciliationReport(String computationId, List<String> documentIds, List<ReconciliationLine> lines, Verdict verdict) {

    public ReconciliationReport {
        if (computationId == null || documentIds == null || lines == null || verdict == null) {
            throw new IllegalArgumentException("A reconciliation report names its computation, certificates, lines and verdict");
        }
        documentIds = List.copyOf(documentIds);
        lines = List.copyOf(lines);
    }
}
