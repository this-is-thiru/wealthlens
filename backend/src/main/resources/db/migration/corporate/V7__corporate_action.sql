-- W-337d1 (D-945): corporate_action and its demerger stocks child table (deferred from W-337b).

CREATE TABLE corporate_action (
    id                   VARCHAR(255) PRIMARY KEY,
    stock_code           TEXT,
    stock_name           TEXT,
    to_stock_code        TEXT,
    to_stock_name        TEXT,
    type                 TEXT,
    asset_type           TEXT,
    description          TEXT,
    record_date          DATE,
    ex_date              DATE,
    allotment_date       DATE,
    priority             INT          NOT NULL DEFAULT 0,
    action_price         TEXT,
    ratio                TEXT,
    demerger_ratio       TEXT,
    demerger_price_ratio TEXT,
    main_stock_code      TEXT,
    main_stock_name      TEXT,
    created_by           TEXT,
    updated_by           TEXT,
    created_at           TIMESTAMP(6),
    updated_at           TIMESTAMP(6)
);
CREATE INDEX corporate_action_stock_record_idx ON corporate_action (stock_code, record_date);
CREATE INDEX corporate_action_type_record_idx ON corporate_action (type, record_date);

CREATE TABLE corporate_action_demerger_stocks (
    corporate_action_id  VARCHAR(255) NOT NULL REFERENCES corporate_action (id),
    stock_code           TEXT,
    stock_name           TEXT
);
CREATE INDEX corporate_action_demerger_stocks_ca_idx ON corporate_action_demerger_stocks (corporate_action_id);
