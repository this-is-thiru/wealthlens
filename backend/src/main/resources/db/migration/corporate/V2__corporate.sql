-- W-337b (D-939): lastly_performed_corporate_action only. corporate_action waits for W-337d — its demerger_detail
-- embeds a list of its own (DemergerDetail.demergerStocks), a shape this item does not design.

CREATE TABLE lastly_performed_corporate_action (
    id          VARCHAR(255) PRIMARY KEY,
    email       TEXT         NOT NULL,
    stock_code  TEXT,
    asset_type  TEXT,
    action_type TEXT,
    action_date DATE,
    broker_name TEXT,
    created_by  TEXT,
    updated_by  TEXT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);
CREATE INDEX lastly_performed_corporate_action_email_idx ON lastly_performed_corporate_action (email);
