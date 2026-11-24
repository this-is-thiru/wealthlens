-- W-337d2 (D-947): charge_schedules, charge_instruments, charge_accounts (+ billing_events),
-- user_charges (+ lines + amount_by_code). charge_catalogue already exists in V4__charge_catalogue.sql.

CREATE TABLE charge_schedules (
    id                          VARCHAR(255) PRIMARY KEY,
    schedule_code               TEXT         NOT NULL,
    broker_name                 TEXT,
    asset_type                  TEXT,
    segment                     TEXT,
    exchange                    TEXT,
    plan_code                   TEXT,
    start_date                  DATE,
    end_date                    DATE,
    status                      TEXT,
    requires_instrument_profile BOOLEAN      NOT NULL DEFAULT FALSE,
    rules_json                  TEXT,
    source_url                  TEXT,
    verified_on                 DATE,
    created_by                  TEXT,
    updated_by                  TEXT,
    created_at                  TIMESTAMP(6),
    updated_at                  TIMESTAMP(6)
);
CREATE UNIQUE INDEX charge_schedules_schedule_code_idx ON charge_schedules (schedule_code);
CREATE INDEX charge_schedules_broker_idx ON charge_schedules (broker_name);

CREATE TABLE charge_instruments (
    id              VARCHAR(255) PRIMARY KEY,
    stock_code      TEXT,
    isin            TEXT,
    name            TEXT,
    asset_type      TEXT,
    fund_category   TEXT,
    equity_oriented BOOLEAN,
    plan_type       TEXT,
    amc             TEXT,
    start_date      DATE,
    end_date        DATE,
    status          TEXT,
    rules_json      TEXT,
    source_url      TEXT,
    verified_on     DATE,
    created_by      TEXT,
    updated_by      TEXT,
    created_at      TIMESTAMP(6),
    updated_at      TIMESTAMP(6)
);
CREATE UNIQUE INDEX charge_instrument_scheme_idx ON charge_instruments (stock_code, start_date);
CREATE INDEX charge_instruments_stock_code_idx ON charge_instruments (stock_code);
CREATE INDEX charge_instruments_isin_idx ON charge_instruments (isin) WHERE isin IS NOT NULL;

CREATE TABLE charge_accounts (
    id                  VARCHAR(255) PRIMARY KEY,
    email               TEXT         NOT NULL,
    account_holder      TEXT,
    broker_name         TEXT,
    demat_account_id    TEXT,
    plan_code           TEXT,
    opened_on           DATE,
    amc_frequency       TEXT,
    last_billed_through DATE,
    status              TEXT,
    created_by          TEXT,
    updated_by          TEXT,
    created_at          TIMESTAMP(6),
    updated_at          TIMESTAMP(6)
);
CREATE UNIQUE INDEX charge_account_idx ON charge_accounts (email, broker_name, demat_account_id);
CREATE INDEX charge_account_amc_due_idx ON charge_accounts (amc_frequency, status, last_billed_through);

CREATE TABLE charge_account_billing_events (
    account_id     VARCHAR(255)     NOT NULL REFERENCES charge_accounts (id),
    user_charge_id TEXT,
    period_from    DATE,
    period_to      DATE,
    charged_on     DATE,
    amount         DOUBLE PRECISION NOT NULL DEFAULT 0
);
CREATE INDEX charge_account_billing_events_account_idx ON charge_account_billing_events (account_id);

CREATE TABLE user_charges (
    id                      VARCHAR(255)     PRIMARY KEY,
    email                   TEXT             NOT NULL,
    account_holder          TEXT,
    broker_name             TEXT,
    asset_type              TEXT,
    segment                 TEXT,
    exchange                TEXT,
    stock_code              TEXT,
    transaction_id          TEXT,
    order_id                TEXT,
    event                   TEXT,
    transaction_date        DATE,
    computed_on             TIMESTAMP(6),
    resolution              TEXT,
    schedule_id             TEXT,
    schedule_code           TEXT,
    statutory_schedule_id   TEXT,
    statutory_schedule_code TEXT,
    instrument_id           TEXT,
    turnover                DOUBLE PRECISION NOT NULL DEFAULT 0,
    quantity                DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_charges           DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_by              TEXT,
    updated_by              TEXT,
    created_at              TIMESTAMP(6),
    updated_at              TIMESTAMP(6)
);
CREATE UNIQUE INDEX user_charge_txn_idx ON user_charges (email, transaction_id);
CREATE INDEX user_charge_dedupe_idx ON user_charges (email, account_holder, broker_name, stock_code, transaction_date);
CREATE INDEX user_charge_history_idx ON user_charges (email, transaction_date DESC);

CREATE TABLE user_charge_lines (
    user_charge_id VARCHAR(255)     NOT NULL REFERENCES user_charges (id),
    line_order     INT              NOT NULL DEFAULT 0,
    code           TEXT,
    display_name   TEXT,
    category       TEXT,
    basis          TEXT,
    source         TEXT,
    rate           DOUBLE PRECISION,
    base_amount    DOUBLE PRECISION NOT NULL DEFAULT 0,
    amount         DOUBLE PRECISION NOT NULL DEFAULT 0,
    taxable        BOOLEAN          NOT NULL DEFAULT FALSE
);
CREATE INDEX user_charge_lines_charge_idx ON user_charge_lines (user_charge_id);

CREATE TABLE user_charge_amounts (
    user_charge_id VARCHAR(255)     NOT NULL REFERENCES user_charges (id),
    charge_code    TEXT             NOT NULL,
    amount         DOUBLE PRECISION NOT NULL DEFAULT 0,
    PRIMARY KEY (user_charge_id, charge_code)
);
