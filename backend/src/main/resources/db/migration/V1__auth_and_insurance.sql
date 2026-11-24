-- W-337a, D-937 probe: user_details, refresh_tokens (+ its factors child table), insurances (+ its policy details
-- child table), as parallel JPA entities proving the mapping. Runs in the Testcontainers database's own default
-- schema -- the "wealthlens" schema convention (D-929) applies when W-337b lays Flyway out against a real host.

CREATE TABLE user_details (
    id                 VARCHAR(255) PRIMARY KEY,
    email              TEXT         NOT NULL,
    password           TEXT,
    roles              TEXT,
    last_signed_in_at  TIMESTAMP(6)
);
CREATE UNIQUE INDEX user_details_email_idx ON user_details (email);

CREATE TABLE refresh_tokens (
    id          VARCHAR(255)    PRIMARY KEY,
    token_hash  TEXT            NOT NULL,
    family_id   TEXT            NOT NULL,
    email       TEXT            NOT NULL,
    issued_at   TIMESTAMPTZ(6)  NOT NULL,
    expires_at  TIMESTAMPTZ(6)  NOT NULL,
    used_at     TIMESTAMPTZ(6),
    revoked_at  TIMESTAMPTZ(6)
);
CREATE UNIQUE INDEX refresh_tokens_token_hash_idx ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_email_idx ON refresh_tokens (email);
CREATE INDEX refresh_tokens_expiry_idx ON refresh_tokens (expires_at);

CREATE TABLE refresh_token_factors (
    refresh_token_id  VARCHAR(255) NOT NULL REFERENCES refresh_tokens (id),
    factor            TEXT         NOT NULL
);
CREATE INDEX refresh_token_factors_token_idx ON refresh_token_factors (refresh_token_id);

CREATE TABLE insurances (
    id                VARCHAR(255)      PRIMARY KEY,
    email             TEXT              NOT NULL,
    port_from         TEXT,
    port_to           TEXT,
    insurance_id      TEXT,
    insurance_type    TEXT,
    policy_type       TEXT,
    premium           DOUBLE PRECISION  NOT NULL,
    start_date        TEXT,
    end_date          TEXT,
    maturity_date     TEXT,
    renewal_date      TEXT,
    maturity_amount   TEXT,
    broker            TEXT,
    agent_name        TEXT,
    agent_email       TEXT,
    agent_contact     TEXT,
    agent_address     TEXT,
    insurance_status  TEXT,
    notes             TEXT
);
CREATE INDEX insurances_email_idx ON insurances (email);

CREATE TABLE insurance_policy_details (
    insurance_id_fk     VARCHAR(255)      NOT NULL REFERENCES insurances (id),
    policy_id           TEXT,
    premium             DOUBLE PRECISION  NOT NULL,
    start_date          DATE,
    end_date            DATE,
    policy_name         TEXT,
    broker_name         TEXT,
    agent_name          TEXT,
    agent_email         TEXT,
    agent_contact       TEXT,
    agent_address       TEXT,
    insurance_status    TEXT,
    notes               TEXT,
    policy_description  TEXT
);
CREATE INDEX insurance_policy_details_insurance_idx ON insurance_policy_details (insurance_id_fk);
