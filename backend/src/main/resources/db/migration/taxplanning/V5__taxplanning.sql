-- W-337b (D-939): 17 of taxplanning's 20 collections. tax_draft_computations, tax_draft_revisions and
-- tax_filing_drafts wait for W-337f — each has a dynamic-keyed map or a single embedded field that itself holds a
-- list (ruleChecksums, WorkbookImport, DraftStaleness), none of them this item's shape to design. tax_document_bytes
-- (GridFS) is W-337f's too.

CREATE TABLE tax_gazette_cursor (
    id          VARCHAR(255) PRIMARY KEY,
    year        INT,
    last_number BIGINT,
    updated_at  TIMESTAMP(6),
    updated_by  TEXT
);

CREATE TABLE tax_gazette_leads (
    id        VARCHAR(255) PRIMARY KEY,
    url       TEXT         NOT NULL,
    year      INT,
    number    BIGINT,
    page      INT,
    note      TEXT,
    found_at  TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_gazette_leads_url_idx ON tax_gazette_leads (url);

CREATE TABLE tax_gazette_lead_matched_terms (
    lead_id       VARCHAR(255) NOT NULL REFERENCES tax_gazette_leads (id),
    matched_term  TEXT         NOT NULL
);
CREATE INDEX tax_gazette_lead_matched_terms_lead_idx ON tax_gazette_lead_matched_terms (lead_id);

CREATE TABLE tax_gazette_lead_possibly_affected (
    lead_id             VARCHAR(255) NOT NULL REFERENCES tax_gazette_leads (id),
    possibly_affected   TEXT         NOT NULL
);
CREATE INDEX tax_gazette_lead_possibly_affected_lead_idx ON tax_gazette_lead_possibly_affected (lead_id);

CREATE TABLE tax_regime_election_history (
    id           VARCHAR(255) PRIMARY KEY,
    profile_id   TEXT         NOT NULL,
    regime_code  TEXT         NOT NULL,
    created_by   TEXT,
    updated_by   TEXT,
    created_at   TIMESTAMP(6),
    updated_at   TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_regime_election_history_idx ON tax_regime_election_history (profile_id, regime_code);

CREATE TABLE tax_regime_election_events (
    history_id       VARCHAR(255) NOT NULL REFERENCES tax_regime_election_history (id),
    tax_year_start   INT          NOT NULL,
    source_draft     TEXT         NOT NULL
);
CREATE INDEX tax_regime_election_events_history_idx ON tax_regime_election_events (history_id);

CREATE TABLE tax_year_workspaces (
    id                VARCHAR(255) PRIMARY KEY,
    profile_id        TEXT         NOT NULL,
    tax_year_start    INT          NOT NULL,
    primary_draft_id  TEXT,
    created_by        TEXT,
    updated_by        TEXT,
    created_at        TIMESTAMP(6),
    updated_at        TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_year_workspaces_idx ON tax_year_workspaces (profile_id, tax_year_start);

-- Append-only; no audit_metadata.
CREATE TABLE tax_access_log (
    id          VARCHAR(255) PRIMARY KEY,
    principal   TEXT,
    subject     TEXT,
    subject_id  TEXT,
    read_at     TIMESTAMPTZ(6)
);
CREATE INDEX tax_access_log_principal_idx ON tax_access_log (principal);

CREATE TABLE tax_employers (
    id          VARCHAR(255) PRIMARY KEY,
    profile_id  TEXT         NOT NULL,
    name        TEXT,
    tan         TEXT,
    tan_digest  TEXT,
    pan         TEXT,
    type        TEXT,
    address     TEXT,
    created_by  TEXT,
    updated_by  TEXT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_employers_tan_idx ON tax_employers (profile_id, tan_digest);

CREATE TABLE tax_family_members (
    id                                 VARCHAR(255) PRIMARY KEY,
    profile_id                         TEXT         NOT NULL,
    name                               TEXT,
    relationship                       TEXT,
    date_of_birth                      DATE,
    dependant                          BOOLEAN,
    disability                         TEXT,
    date_of_death                      DATE,
    multiple_birth_after_first_child   BOOLEAN,
    resident_in_india                  BOOLEAN,
    pan                                TEXT,
    created_by                         TEXT,
    updated_by                         TEXT,
    created_at                         TIMESTAMP(6),
    updated_at                         TIMESTAMP(6)
);
CREATE INDEX tax_family_members_profile_idx ON tax_family_members (profile_id);

CREATE TABLE tax_bank_accounts (
    id                     VARCHAR(255) PRIMARY KEY,
    profile_id             TEXT         NOT NULL,
    bank_name              TEXT,
    ifsc                   TEXT,
    masked_number          TEXT,
    kind                   TEXT,
    nominated_for_refund   BOOLEAN,
    created_by             TEXT,
    updated_by             TEXT,
    created_at             TIMESTAMP(6),
    updated_at             TIMESTAMP(6)
);
CREATE INDEX tax_bank_accounts_profile_idx ON tax_bank_accounts (profile_id);

CREATE TABLE tax_profile_erasures (
    id            VARCHAR(255) PRIMARY KEY,
    email         TEXT,
    profile_id    TEXT,
    requested_at  TIMESTAMP(6),
    erased_at     TIMESTAMP(6),
    created_by    TEXT,
    updated_by    TEXT,
    created_at    TIMESTAMP(6),
    updated_at    TIMESTAMP(6)
);

CREATE TABLE tax_profiles (
    id                     VARCHAR(255) PRIMARY KEY,
    email                  TEXT,
    pan                    TEXT,
    pan_digest             TEXT,
    name                   TEXT,
    kind                   TEXT,
    date_of_birth          DATE,
    residential_status     TEXT,
    holds_portfolio        BOOLEAN,
    disability             TEXT,
    date_of_death          DATE,
    erasure_requested_at   TIMESTAMP(6),
    created_by             TEXT,
    updated_by             TEXT,
    created_at             TIMESTAMP(6),
    updated_at             TIMESTAMP(6)
);
CREATE INDEX tax_profiles_email_idx ON tax_profiles (email);
CREATE UNIQUE INDEX tax_profiles_pan_digest_idx ON tax_profiles (pan_digest);

CREATE TABLE tax_documents (
    id           VARCHAR(255) PRIMARY KEY,
    profile_id   TEXT         NOT NULL,
    tax_year_start  INT,
    document_type   TEXT,
    sha256       TEXT,
    uploaded_at  TIMESTAMP(6),
    page_count   INT,
    scanned      BOOLEAN,
    created_by   TEXT,
    updated_by   TEXT,
    created_at   TIMESTAMP(6),
    updated_at   TIMESTAMP(6)
);
CREATE UNIQUE INDEX tax_documents_profile_sha_idx ON tax_documents (profile_id, sha256);
CREATE INDEX tax_documents_profile_year_idx ON tax_documents (profile_id, tax_year_start);

CREATE TABLE tax_parse_attempts (
    id                   VARCHAR(255) PRIMARY KEY,
    document_id          TEXT         NOT NULL,
    parser_id             TEXT,
    parser_version        TEXT,
    started_at            TIMESTAMP(6),
    finished_at           TIMESTAMP(6),
    outcome               TEXT,
    fields_json           TEXT,
    unmapped_text_json    TEXT,
    parser_notes_json     TEXT,
    created_by            TEXT,
    updated_by            TEXT,
    created_at             TIMESTAMP(6),
    updated_at             TIMESTAMP(6)
);
CREATE INDEX tax_parse_attempts_document_idx ON tax_parse_attempts (document_id, started_at);

CREATE TABLE tax_document_confirmations (
    id             VARCHAR(255) PRIMARY KEY,
    confirmed_by   TEXT,
    confirmed_at   TIMESTAMP(6),
    accepted_json  TEXT,
    created_by     TEXT,
    updated_by     TEXT,
    created_at     TIMESTAMP(6),
    updated_at     TIMESTAMP(6)
);

CREATE TABLE tax_document_confirmation_rejected_fields (
    confirmation_id  VARCHAR(255) NOT NULL REFERENCES tax_document_confirmations (id),
    rejected_field   TEXT         NOT NULL
);
CREATE INDEX tax_document_confirmation_rejected_fields_idx ON tax_document_confirmation_rejected_fields (confirmation_id);

-- The id is the job's name, not a generated key (JobLease.job is the @MongoId field); the findAndModify-upsert
-- locking itself is W-337f's code, not this item's schema.
CREATE TABLE tax_job_leases (
    job         VARCHAR(255) PRIMARY KEY,
    holder      TEXT,
    expires_at  TIMESTAMPTZ(6)
);

CREATE TABLE tax_job_failures (
    id         VARCHAR(255) PRIMARY KEY,
    job        TEXT,
    subject    TEXT,
    reason     TEXT,
    failed_at  TIMESTAMP(6)
);

CREATE TABLE tax_lenders (
    id          VARCHAR(255) PRIMARY KEY,
    profile_id  TEXT         NOT NULL,
    name        TEXT,
    address     TEXT,
    pan         TEXT,
    kind        TEXT,
    created_by  TEXT,
    updated_by  TEXT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);
CREATE INDEX tax_lenders_profile_idx ON tax_lenders (profile_id);

CREATE TABLE tax_landlords (
    id                       VARCHAR(255) PRIMARY KEY,
    profile_id               TEXT         NOT NULL,
    name                     TEXT,
    address                  TEXT,
    pan                      TEXT,
    relationship_to_taxpayer TEXT,
    created_by               TEXT,
    updated_by               TEXT,
    created_at               TIMESTAMP(6),
    updated_at               TIMESTAMP(6)
);
CREATE INDEX tax_landlords_profile_idx ON tax_landlords (profile_id);
