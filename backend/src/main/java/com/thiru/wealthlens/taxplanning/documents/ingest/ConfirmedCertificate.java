package com.thiru.wealthlens.taxplanning.documents.ingest;

/**
 * A certificate as the person confirmed it (W-131d2, D-765 C1a): its document, for the notices and the report to name, and the
 * confirmation whose accepted values are the only ones used ({@code DR 05} M4) — a confirmation alone names only its attempt.
 */
public record ConfirmedCertificate(String documentId, DocumentConfirmation confirmation) {

    public ConfirmedCertificate {
        Checks.text(documentId, "A confirmed certificate names its document");
        Checks.present(confirmation, "A confirmed certificate carries its confirmation");
    }
}
