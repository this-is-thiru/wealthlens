-- W-407d (D-966's rule): m1.6.1's pnl_user_year_idx was unique and not sparse, so MongoDB refused a second document with no
-- financial_year for one email; financial_year can be NULL here, so the index is rebuilt NULLS NOT DISTINCT to keep that refusal.
DROP INDEX pnl_user_year_idx;
CREATE UNIQUE INDEX pnl_user_year_idx ON profit_and_loss (email, financial_year) NULLS NOT DISTINCT;
