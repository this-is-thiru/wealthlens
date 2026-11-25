-- W-407e (D-966's rule): m1.6.1's unique indexes on these were not sparse, so MongoDB refused a second document missing the
-- nullable field; each is rebuilt NULLS NOT DISTINCT to keep that refusal.
DROP INDEX tax_draft_computations_receipt_idx;
CREATE UNIQUE INDEX tax_draft_computations_receipt_idx ON tax_draft_computations (receipt) NULLS NOT DISTINCT;

DROP INDEX tax_employers_tan_idx;
CREATE UNIQUE INDEX tax_employers_tan_idx ON tax_employers (profile_id, tan_digest) NULLS NOT DISTINCT;

DROP INDEX tax_documents_profile_sha_idx;
CREATE UNIQUE INDEX tax_documents_profile_sha_idx ON tax_documents (profile_id, sha256) NULLS NOT DISTINCT;
