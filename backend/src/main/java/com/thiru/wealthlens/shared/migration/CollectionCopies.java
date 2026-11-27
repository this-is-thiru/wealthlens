package com.thiru.wealthlens.shared.migration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Every collection the {@code m1.6.1} build stores, and the copy specified for each (W-337g, D-953 C6a). The copier runs
 * only when every one of {@link #SOURCE} has a specification, so it can never move part of the data and leave the rest.
 */
final class CollectionCopies {

    /** The forty {@code @Document} collections at {@code m1.6.1}, the build prod and staging run. */
    static final Set<String> SOURCE = Set.of(
            "user_details", "refresh_tokens", "insurances",
            "charge_catalogue", "charge_schedules", "charge_instruments", "charge_accounts", "user_charges",
            "corporate_action", "lastly_performed_corporate_action",
            "assets", "transactions", "trade_outcomes", "profit_and_loss",
            "privacy_erasure_requests", "privacy_breach_intimations", "privacy_breach_notices", "privacy_grievances",
            "privacy_consents", "privacy_processing_log",
            "tax_profiles", "tax_family_members", "tax_employers", "tax_landlords", "tax_lenders", "tax_bank_accounts",
            "tax_profile_erasures", "tax_year_workspaces", "tax_filing_drafts", "tax_draft_revisions",
            "tax_regime_election_history", "tax_draft_computations", "tax_documents", "tax_parse_attempts",
            "tax_document_confirmations", "tax_gazette_cursor", "tax_gazette_leads", "tax_job_failures", "tax_job_leases",
            "tax_access_log");

    /** The GridFS bucket {@code m1.6.1} kept a document's sealed bytes in, and its two collections. */
    static final String DOCUMENT_BYTES_BUCKET = "tax_document_bytes";
    static final Set<String> DOCUMENT_BYTES_COLLECTIONS = Set.of(DOCUMENT_BYTES_BUCKET + ".files", DOCUMENT_BYTES_BUCKET + ".chunks");

    /** The collections holding no embedded data (W-337g1) and those whose lists, maps and sub-documents fill child tables and columns (W-337g2a1 – a3), and those holding JSON columns (W-337g2b1 – b3): every one of {@link #SOURCE}. */
    static final List<CollectionCopy> SPECIFIED = List.of(
            CollectionCopy.sameName("user_details"),
            CollectionCopy.sameName("charge_catalogue"),
            CollectionCopy.sameName("lastly_performed_corporate_action"),
            CollectionCopy.sameName("privacy_erasure_requests"),
            CollectionCopy.sameName("privacy_breach_intimations"),
            CollectionCopy.sameName("privacy_grievances"),
            CollectionCopy.sameName("privacy_consents"),
            CollectionCopy.sameName("privacy_processing_log"),
            new CollectionCopy("tax_documents", "tax_documents", Map.of("tax_year", "tax_year_start"), Set.of(), List.of(), List.of(), Set.of()),
            CollectionCopy.sameName("tax_parse_attempts"),
            CollectionCopy.sameName("tax_gazette_cursor"),
            CollectionCopy.sameName("tax_job_failures"),
            CollectionCopy.sameName("tax_job_leases"),
            CollectionCopy.sameName("tax_bank_accounts"),
            CollectionCopy.sameName("tax_employers"),
            CollectionCopy.sameName("tax_family_members"),
            CollectionCopy.sameName("tax_landlords"),
            CollectionCopy.sameName("tax_lenders"),
            CollectionCopy.sameName("tax_profile_erasures"),
            CollectionCopy.sameName("tax_profiles"),
            CollectionCopy.sameName("tax_access_log"),
            new CollectionCopy("tax_year_workspaces", "tax_year_workspaces", Map.of("tax_year", "tax_year_start"), Set.of(), List.of(), List.of(), Set.of()),
            new CollectionCopy("refresh_tokens", "refresh_tokens", Map.of(), Set.of(),
                    List.of(ChildCopy.values("factors", "refresh_token_factors", "refresh_token_id", "factor")), List.of(), Set.of()),
            // m1.6.1's InsuranceEntity.policyDetails had no @Field, so the list is stored under its Java name
            new CollectionCopy("insurances", "insurances", Map.of(), Set.of(),
                    List.of(ChildCopy.documents("policyDetails", "insurance_policy_details", "insurance_id_fk")), List.of(), Set.of()),
            new CollectionCopy("privacy_breach_notices", "privacy_breach_notices", Map.of(), Set.of(),
                    List.of(ChildCopy.values("affected", "privacy_breach_notice_affected", "notice_id", "affected")), List.of(), Set.of()),
            new CollectionCopy("charge_accounts", "charge_accounts", Map.of(), Set.of(),
                    List.of(ChildCopy.documents("billing_events", "charge_account_billing_events", "account_id")), List.of(), Set.of()),
            new CollectionCopy("user_charges", "user_charges", Map.of(), Set.of(), List.of(
                    ChildCopy.orderedDocuments("lines", "user_charge_lines", "user_charge_id", "line_order"),
                    ChildCopy.entries("amount_by_code", "user_charge_amounts", "user_charge_id", "charge_code", "amount")), List.of(), Set.of()),
            // DemergerDetail is @Embedded: its scalars are corporate_action's own columns, its stocks a child table
            new CollectionCopy("corporate_action", "corporate_action", Map.of(), Set.of("demerger_detail"),
                    List.of(ChildCopy.documents("demerger_stocks", "corporate_action_demerger_stocks", "corporate_action_id")), List.of(), Set.of()),
            // ElectionEvent is a record with no @Field: its keys are stored as taxYearStart and sourceDraft, snake_cased on the way
            new CollectionCopy("tax_regime_election_history", "tax_regime_election_history", Map.of(), Set.of(),
                    List.of(ChildCopy.documents("events", "tax_regime_election_events", "history_id")), List.of(), Set.of()),
            new CollectionCopy("tax_document_confirmations", "tax_document_confirmations", Map.of(), Set.of(), List.of(
                    ChildCopy.values("rejected_fields", "tax_document_confirmation_rejected_fields", "confirmation_id", "rejected_field")), List.of(), Set.of()),
            new CollectionCopy("tax_gazette_leads", "tax_gazette_leads", Map.of(), Set.of(), List.of(
                    ChildCopy.values("matched_terms", "tax_gazette_lead_matched_terms", "lead_id", "matched_term"),
                    ChildCopy.values("possibly_affected", "tax_gazette_lead_possibly_affected", "lead_id", "possibly_affected")), List.of(), Set.of()),
            new CollectionCopy("charge_schedules", "charge_schedules", Map.of(), Set.of(), List.of(),
                    List.of(new JsonCopy("rules", "rules_json", Map.of())), Set.of()),
            new CollectionCopy("charge_instruments", "charge_instruments", Map.of(), Set.of(), List.of(),
                    List.of(new JsonCopy("rules", "rules_json", Map.of())), Set.of()),
            new CollectionCopy("tax_filing_drafts", "tax_filing_drafts", Map.of("tax_year", "tax_year_start"), Set.of(), List.of(),
                    List.of(JsonCopy.sameName("staleness")), Set.of()),
            // DraftRevision.setImportedFrom keeps imported_from_workbook_sha256 in step with the JSON (plans/W-337g2b1.md C3a)
            new CollectionCopy("tax_draft_revisions", "tax_draft_revisions", Map.of(), Set.of(), List.of(),
                    List.of(new JsonCopy("imported_from", "imported_from", Map.of("workbook_sha256", "imported_from_workbook_sha256"))), Set.of()),
            new CollectionCopy("tax_draft_computations", "tax_draft_computations", Map.of(), Set.of(),
                    List.of(ChildCopy.orderedValues("law_and_exec", "tax_draft_computation_law_and_exec", "draft_computation_id",
                            "version", "position")),
                    List.of(JsonCopy.sameName("rule_checksums")), Set.of()),
            new CollectionCopy("assets", "assets", Map.of(), Set.of(), List.of(), List.of(
                    new JsonCopy("order_time_quantities", "order_time_quantities_json", Map.of()),
                    new JsonCopy("buy_transaction_ids", "buy_transaction_ids_json", Map.of()),
                    new JsonCopy("sell_transaction_ids", "sell_transaction_ids_json", Map.of()),
                    new JsonCopy("corporate_actions", "corporate_actions_json", Map.of())), Set.of()),
            // submitted_at: LocalDateTime.now() at m1.6.1 (TransactionService.java:101), local-held (D-975 C3a)
            new CollectionCopy("transactions", "transactions", Map.of(), Set.of(), List.of(), List.of(
                    new JsonCopy("corporate_actions", "corporate_actions_json", Map.of()),
                    new JsonCopy("asset_request", "asset_request_json", Map.of())), Set.of("submitted_at")),
            new CollectionCopy("trade_outcomes", "trade_outcomes", Map.of(), Set.of(), List.of(), List.of(
                    new JsonCopy("buy_charge_breakup", "buy_charge_breakup_json", Map.of()),
                    new JsonCopy("sell_charge_breakup", "sell_charge_breakup_json", Map.of()),
                    new JsonCopy("applied_corporate_actions", "applied_corporate_actions_json", Map.of())), Set.of()),
            new CollectionCopy("profit_and_loss", "profit_and_loss", Map.of(), Set.of(), List.of(), List.of(
                    new JsonCopy("realised_profits", "realised_profits_json", Map.of()),
                    new JsonCopy("out_sourced_realised_profits", "out_sourced_realised_profits_json", Map.of())), Set.of()));

    private CollectionCopies() {
    }

    /** The source collections no copy is specified for, in name order. */
    static Set<String> unspecified() {
        Set<String> specified = SPECIFIED.stream().map(CollectionCopy::collection).collect(Collectors.toSet());
        return SOURCE.stream().filter(name -> !specified.contains(name)).collect(Collectors.toCollection(TreeSet::new));
    }
}
