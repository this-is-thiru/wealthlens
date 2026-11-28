-- W-428c (D-1039): personal data is stored plain on every database (W-428b, D-1038), so what existed only to encrypt goes.
-- C2a: the dates of birth were made TEXT by V10 only to hold ciphertext; every stored value is LocalDate.toString(), ISO.
ALTER TABLE tax_profiles ALTER COLUMN date_of_birth TYPE DATE USING date_of_birth::date;
ALTER TABLE tax_family_members ALTER COLUMN date_of_birth TYPE DATE USING date_of_birth::date;

-- C1a: a PAN is unique among profiles not held for erasure, as its digest was (the digest was cleared on a hold, W-235).
DROP INDEX tax_profiles_pan_digest_idx;
ALTER TABLE tax_profiles DROP COLUMN pan_digest;
CREATE UNIQUE INDEX tax_profiles_pan_idx ON tax_profiles (pan) WHERE erasure_requested_at IS NULL;

-- A TAN is unique within a profile, a missing one included, as its digest was (V13).
DROP INDEX tax_employers_tan_idx;
ALTER TABLE tax_employers DROP COLUMN tan_digest;
CREATE UNIQUE INDEX tax_employers_tan_idx ON tax_employers (profile_id, tan) NULLS NOT DISTINCT;
