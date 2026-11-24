-- W-337c1 (D-941): audit columns for insurances, matching InsuranceEntity's AuditableEntity contract and V2 - V5.

ALTER TABLE insurances
    ADD COLUMN created_by TEXT,
    ADD COLUMN updated_by TEXT,
    ADD COLUMN created_at TIMESTAMP(6),
    ADD COLUMN updated_at TIMESTAMP(6);
