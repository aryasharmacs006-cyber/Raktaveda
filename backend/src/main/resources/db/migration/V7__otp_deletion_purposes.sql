-- Stage 5 fix: account deletion uses its own one-time code purposes, so a login code can never confirm a deletion.
-- (The constraint was created without a name in V1; PostgreSQL named it otp_challenges_purpose_check.)
ALTER TABLE otp_challenges DROP CONSTRAINT otp_challenges_purpose_check;
ALTER TABLE otp_challenges ADD CONSTRAINT otp_challenges_purpose_check
    CHECK (purpose IN ('USER_LOGIN', 'INSTITUTION_STEP2', 'ADMIN_STEP2', 'USER_DELETE', 'INSTITUTION_DELETE'));
