-- W-407c G-1: MongoDB's unique indexes refused a second missing (null) value; PostgreSQL's treat NULLs as distinct unless told
-- otherwise. These three cover a column that can be NULL, so each is rebuilt NULLS NOT DISTINCT (PostgreSQL 15+) to keep the
-- refusal m1.6.1 had.
DROP INDEX charge_instrument_scheme_idx;
CREATE UNIQUE INDEX charge_instrument_scheme_idx ON charge_instruments (stock_code, start_date) NULLS NOT DISTINCT;

DROP INDEX charge_account_idx;
CREATE UNIQUE INDEX charge_account_idx ON charge_accounts (email, broker_name, demat_account_id) NULLS NOT DISTINCT;

DROP INDEX user_charge_txn_idx;
CREATE UNIQUE INDEX user_charge_txn_idx ON user_charges (email, transaction_id) NULLS NOT DISTINCT;
