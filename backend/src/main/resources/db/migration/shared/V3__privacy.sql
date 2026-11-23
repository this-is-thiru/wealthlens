-- W-337b (D-939): the six privacy_* tables; all flat except privacy_breach_notices' affected list, the same shape
-- RefreshTokenJpa.factors already proved (W-337a), here with SearchableColumn on the child column.

CREATE TABLE privacy_erasure_requests (
    id           VARCHAR(255) PRIMARY KEY,
    email        TEXT         NOT NULL,
    requested_at TIMESTAMP(6),
    cause        TEXT,
    erased_at    TIMESTAMP(6),
    created_by   TEXT,
    updated_by   TEXT,
    created_at   TIMESTAMP(6),
    updated_at   TIMESTAMP(6)
);
CREATE INDEX privacy_erasure_requests_email_idx ON privacy_erasure_requests (email);

CREATE TABLE privacy_consents (
    id              VARCHAR(255) PRIMARY KEY,
    email           TEXT         NOT NULL,
    notice_version  TEXT,
    given_at        TIMESTAMP(6),
    noticed_at      TIMESTAMP(6),
    created_by      TEXT,
    updated_by      TEXT,
    created_at      TIMESTAMP(6),
    updated_at      TIMESTAMP(6)
);
CREATE UNIQUE INDEX privacy_consents_email_idx ON privacy_consents (email);

CREATE TABLE privacy_breach_intimations (
    id          VARCHAR(255) PRIMARY KEY,
    notice_id   TEXT,
    email       TEXT,
    posted_at   TIMESTAMP(6),
    read_at     TIMESTAMP(6),
    emailed_at  TIMESTAMP(6),
    created_by  TEXT,
    updated_by  TEXT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);
CREATE INDEX privacy_breach_intimations_notice_idx ON privacy_breach_intimations (notice_id);
CREATE INDEX privacy_breach_intimations_email_idx ON privacy_breach_intimations (email);

-- Append-only log; no audit_metadata (not an AuditableEntity).
CREATE TABLE privacy_processing_log (
    id             VARCHAR(255) PRIMARY KEY,
    principal      TEXT,
    subject_email  TEXT,
    method         TEXT,
    route          TEXT,
    status         INT,
    remote_address TEXT,
    recorded_at    TIMESTAMP(6),
    request_id     TEXT
);
CREATE INDEX privacy_processing_log_principal_idx ON privacy_processing_log (principal);
CREATE INDEX privacy_processing_log_recorded_at_idx ON privacy_processing_log (recorded_at);

CREATE TABLE privacy_grievances (
    id           VARCHAR(255) PRIMARY KEY,
    email        TEXT,
    text         TEXT,
    received_at  TIMESTAMP(6),
    respond_by   TIMESTAMP(6),
    answer       TEXT,
    answered_at  TIMESTAMP(6),
    created_by   TEXT,
    updated_by   TEXT,
    created_at   TIMESTAMP(6),
    updated_at   TIMESTAMP(6)
);
CREATE INDEX privacy_grievances_email_idx ON privacy_grievances (email);

CREATE TABLE privacy_breach_notices (
    id                         VARCHAR(255) PRIMARY KEY,
    description                TEXT,
    consequences               TEXT,
    mitigation                 TEXT,
    safety_measures            TEXT,
    became_aware_at            TIMESTAMP(6),
    scope                      TEXT,
    posted_at                  TIMESTAMP(6),
    board_report_due_at        TIMESTAMP(6),
    board_extension_reference  TEXT,
    board_first_intimated_at   TIMESTAMP(6),
    board_report_sent_at       TIMESTAMP(6),
    created_by                 TEXT,
    updated_by                 TEXT,
    created_at                 TIMESTAMP(6),
    updated_at                 TIMESTAMP(6)
);

CREATE TABLE privacy_breach_notice_affected (
    notice_id  VARCHAR(255) NOT NULL REFERENCES privacy_breach_notices (id),
    affected   TEXT         NOT NULL
);
CREATE INDEX privacy_breach_notice_affected_notice_idx ON privacy_breach_notice_affected (notice_id);
