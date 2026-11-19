package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.reconcile.ReconciliationReport;
import java.util.List;

/**
 * A draft's confirmed certificates against a computation of it (W-131d2, D-765 C1a): how they fit together (W-129), what they
 * say against the computation (W-130), and which of the year's certificates were left out because nobody confirmed them.
 *
 * @param revision the draft's revision computed
 * @param regime the regime computed under: the draft's election, else the year's default
 * @param unconfirmedDocumentIds the year's certificates with no confirmed reading, so not compared
 */
public record DraftReconciliation(String draftId, int revision, String regime, Assembly assembly, ReconciliationReport report,
        List<String> unconfirmedDocumentIds) {

    public DraftReconciliation {
        unconfirmedDocumentIds = List.copyOf(unconfirmedDocumentIds);
    }
}
