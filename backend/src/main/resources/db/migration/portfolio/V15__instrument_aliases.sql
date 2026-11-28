-- W-440b (D-1055, C14a): the instrument registry's aliases. Each value a broker's file writes for an instrument (its ISIN, the code
-- Upstox writes, a broker's own name for it) names the one stock_code the app stores trades under. Unique on (kind, broker_name,
-- value) NULLS NOT DISTINCT, so an ISIN (no broker) or a broker's value can never name two codes. Written only by
-- POST /api/v1/admin/portfolio/instrument-aliases (SUPER_USER, C15a); no seed, since the list is the owner's holdings.
CREATE TABLE instrument_aliases (
    id          VARCHAR(255) PRIMARY KEY,
    kind        TEXT         NOT NULL,
    broker_name TEXT,
    value       TEXT         NOT NULL,
    stock_code  TEXT         NOT NULL,
    created_by  TEXT,
    updated_by  TEXT,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6)
);
CREATE UNIQUE INDEX instrument_alias_value_idx ON instrument_aliases (kind, broker_name, value) NULLS NOT DISTINCT;
