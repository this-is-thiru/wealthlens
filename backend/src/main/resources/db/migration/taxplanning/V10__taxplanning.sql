-- W-337f (D-951): the 3 taxplanning collections V5 (W-337b's baseline) deferred — tax_draft_computations,
-- tax_draft_revisions, tax_filing_drafts, each with a dynamic-keyed map or a single embedded field that itself
-- holds a list (ruleChecksums, WorkbookImport, DraftStaleness) — plus tax_document_bytes, replacing the GridFS
-- bucket. Also: audit columns on the 5 of V5's 17 tables that had none (C7a), and two column-type fixes V5's own
-- DDL got wrong for a field this item found is encrypted (FamilyMember/TaxpayerProfile.dateOfBirth: ciphertext is
-- text, not a date), and NOT NULL defaults on every primitive-backed column.

ALTER TABLE tax_gazette_cursor ADD COLUMN created_by TEXT;
ALTER TABLE tax_gazette_cursor ADD COLUMN created_at TIMESTAMP(6);

ALTER TABLE tax_gazette_leads ADD COLUMN created_by TEXT;
ALTER TABLE tax_gazette_leads ADD COLUMN updated_by TEXT;
ALTER TABLE tax_gazette_leads ADD COLUMN created_at TIMESTAMP(6);
ALTER TABLE tax_gazette_leads ADD COLUMN updated_at TIMESTAMP(6);

ALTER TABLE tax_access_log ADD COLUMN created_by TEXT;
ALTER TABLE tax_access_log ADD COLUMN updated_by TEXT;
ALTER TABLE tax_access_log ADD COLUMN created_at TIMESTAMP(6);
ALTER TABLE tax_access_log ADD COLUMN updated_at TIMESTAMP(6);

ALTER TABLE tax_job_failures ADD COLUMN created_by TEXT;
ALTER TABLE tax_job_failures ADD COLUMN updated_by TEXT;
ALTER TABLE tax_job_failures ADD COLUMN created_at TIMESTAMP(6);
ALTER TABLE tax_job_failures ADD COLUMN updated_at TIMESTAMP(6);

ALTER TABLE tax_job_leases ADD COLUMN created_by TEXT;
ALTER TABLE tax_job_leases ADD COLUMN updated_by TEXT;
ALTER TABLE tax_job_leases ADD COLUMN created_at TIMESTAMP(6);
ALTER TABLE tax_job_leases ADD COLUMN updated_at TIMESTAMP(6);

ALTER TABLE tax_family_members ALTER COLUMN date_of_birth TYPE TEXT;
ALTER TABLE tax_profiles ALTER COLUMN date_of_birth TYPE TEXT;

-- Every column behind a primitive (int/long/boolean) field is NOT NULL with a zero default, as V9 does for
-- portfolio: Hibernate cannot put a NULL into a primitive, so a nullable column there fails the row's read.
ALTER TABLE tax_gazette_cursor ALTER COLUMN year SET DEFAULT 0;
ALTER TABLE tax_gazette_cursor ALTER COLUMN year SET NOT NULL;
ALTER TABLE tax_gazette_cursor ALTER COLUMN last_number SET DEFAULT 0;
ALTER TABLE tax_gazette_cursor ALTER COLUMN last_number SET NOT NULL;
ALTER TABLE tax_gazette_leads ALTER COLUMN year SET DEFAULT 0;
ALTER TABLE tax_gazette_leads ALTER COLUMN year SET NOT NULL;
ALTER TABLE tax_gazette_leads ALTER COLUMN number SET DEFAULT 0;
ALTER TABLE tax_gazette_leads ALTER COLUMN number SET NOT NULL;
ALTER TABLE tax_gazette_leads ALTER COLUMN page SET DEFAULT 0;
ALTER TABLE tax_gazette_leads ALTER COLUMN page SET NOT NULL;
ALTER TABLE tax_year_workspaces ALTER COLUMN tax_year_start SET DEFAULT 0;
ALTER TABLE tax_year_workspaces ALTER COLUMN tax_year_start SET NOT NULL;
ALTER TABLE tax_family_members ALTER COLUMN dependant SET DEFAULT FALSE;
ALTER TABLE tax_family_members ALTER COLUMN dependant SET NOT NULL;
ALTER TABLE tax_family_members ALTER COLUMN multiple_birth_after_first_child SET DEFAULT FALSE;
ALTER TABLE tax_family_members ALTER COLUMN multiple_birth_after_first_child SET NOT NULL;
ALTER TABLE tax_family_members ALTER COLUMN resident_in_india SET DEFAULT FALSE;
ALTER TABLE tax_family_members ALTER COLUMN resident_in_india SET NOT NULL;
ALTER TABLE tax_bank_accounts ALTER COLUMN nominated_for_refund SET DEFAULT FALSE;
ALTER TABLE tax_bank_accounts ALTER COLUMN nominated_for_refund SET NOT NULL;
ALTER TABLE tax_profiles ALTER COLUMN holds_portfolio SET DEFAULT FALSE;
ALTER TABLE tax_profiles ALTER COLUMN holds_portfolio SET NOT NULL;
ALTER TABLE tax_documents ALTER COLUMN tax_year_start SET DEFAULT 0;
ALTER TABLE tax_documents ALTER COLUMN tax_year_start SET NOT NULL;
ALTER TABLE tax_documents ALTER COLUMN page_count SET DEFAULT 0;
ALTER TABLE tax_documents ALTER COLUMN page_count SET NOT NULL;
ALTER TABLE tax_documents ALTER COLUMN scanned SET DEFAULT FALSE;
ALTER TABLE tax_documents ALTER COLUMN scanned SET NOT NULL;

CREATE TABLE tax_draft_computations (
    id                  VARCHAR(255) PRIMARY KEY,
    receipt             TEXT,
    email               TEXT         NOT NULL,
    draft_id            TEXT,
    revision            INT          NOT NULL DEFAULT 0,
    result_json         TEXT,
    rule_checksums      TEXT,
    computed_at         TIMESTAMP(6),
    created_by          TEXT,
    updated_by          TEXT,
    created_at          TIMESTAMP(6),
    updated_at          TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_draft_computations_receipt_idx ON tax_draft_computations (receipt);
CREATE INDEX tax_draft_computations_draft_idx ON tax_draft_computations (draft_id);

-- Order matters: ComputationReportService compares this list with equals.
CREATE TABLE tax_draft_computation_law_and_exec (
    draft_computation_id  VARCHAR(255) NOT NULL REFERENCES tax_draft_computations (id),
    position              INT          NOT NULL,
    version                TEXT         NOT NULL
);
CREATE INDEX tax_draft_computation_law_and_exec_idx ON tax_draft_computation_law_and_exec (draft_computation_id);

CREATE TABLE tax_draft_revisions (
    id                            VARCHAR(255) PRIMARY KEY,
    draft_id                      TEXT         NOT NULL,
    revision_number                INT          NOT NULL DEFAULT 0,
    full_state_json                TEXT,
    change_set_json                TEXT,
    changed_by                     TEXT,
    reopened                        BOOLEAN      NOT NULL DEFAULT FALSE,
    imported_from                  TEXT,
    imported_from_workbook_sha256  TEXT,
    created_by                      TEXT,
    updated_by                      TEXT,
    created_at                      TIMESTAMP(6),
    updated_at                      TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_draft_revisions_number_idx ON tax_draft_revisions (draft_id, revision_number);

CREATE TABLE tax_filing_drafts (
    id                VARCHAR(255) PRIMARY KEY,
    workspace_id       TEXT         NOT NULL,
    profile_id         TEXT         NOT NULL,
    tax_year_start     INT          NOT NULL,
    label              TEXT,
    status             TEXT,
    current_revision   INT          NOT NULL DEFAULT 0,
    inputs_json        TEXT,
    regime_election    TEXT,
    acted_on_at        TIMESTAMP(6),
    archived_at        TIMESTAMP(6),
    staleness          TEXT,
    created_by         TEXT,
    updated_by         TEXT,
    created_at         TIMESTAMP(6),
    updated_at         TIMESTAMP(6)
);
CREATE INDEX tax_filing_drafts_workspace_idx ON tax_filing_drafts (workspace_id, status);

-- Replaces the tax_document_bytes GridFS bucket (W-131b, D-762 C2a): one row per document, keyed by the document's
-- own id, bytes already sealed by FieldCipher before they reach this column.
CREATE TABLE tax_document_bytes (
    document_id VARCHAR(255) PRIMARY KEY,
    bytes       BYTEA        NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX tax_document_bytes_uploaded_idx ON tax_document_bytes (uploaded_at);
