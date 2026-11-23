-- W-337b (D-939): charge_catalogue only. user_charges, charge_schedules, charge_accounts and charge_instruments
-- wait for W-337d (lines, amount_by_code and other embedded shapes this item does not design).

CREATE TABLE charge_catalogue (
    id                   VARCHAR(255) PRIMARY KEY,
    code                 TEXT         NOT NULL,
    display_name         TEXT,
    category             TEXT,
    description          TEXT,
    statutory_reference  TEXT,
    status               TEXT,
    created_by           TEXT,
    updated_by           TEXT,
    created_at           TIMESTAMP(6),
    updated_at           TIMESTAMP(6)
);
CREATE UNIQUE INDEX charge_catalogue_code_idx ON charge_catalogue (code);
