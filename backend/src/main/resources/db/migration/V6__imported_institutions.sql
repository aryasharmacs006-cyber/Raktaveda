-- Stage 6: institutions loaded from verified official data (CSV import).
-- An imported institution is a listing only. It has no email or password, so nobody can log in as it.
-- (A registered institution still must have both.)
ALTER TABLE institutions ADD COLUMN source varchar(10) NOT NULL DEFAULT 'REGISTERED'
    CHECK (source IN ('REGISTERED', 'IMPORTED'));
ALTER TABLE institutions ALTER COLUMN email DROP NOT NULL;
ALTER TABLE institutions ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE institutions ADD CONSTRAINT chk_registered_has_login
    CHECK (source = 'IMPORTED' OR (email IS NOT NULL AND password_hash IS NOT NULL));
