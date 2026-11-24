-- Raktaveda schema, migration 1 (Stage 1: accounts, OTP, tokens, audit).
-- Later stages add their own tables in V2, V3, ... Never edit a migration that has
-- already run on a real database; add a new one instead.

CREATE EXTENSION IF NOT EXISTS postgis;

-- USER accounts (donor, patient or family). Login is by phone + one-time code.
-- phone_enc  : phone number encrypted with AES-GCM (random each time, so it cannot be searched).
-- phone_index: keyed hash (HMAC) of the phone number. Lets us find a user by phone
--              without storing the phone in plain text.
CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_index   text        NOT NULL UNIQUE,
    phone_enc     text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz
);

-- INSTITUTION accounts (hospital or blood bank). Must be verified by an admin before acting.
CREATE TABLE institutions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(200) NOT NULL,
    type                varchar(20)  NOT NULL CHECK (type IN ('HOSPITAL', 'BLOOD_BANK')),
    licence_number      varchar(100) NOT NULL,
    email               varchar(254) NOT NULL,          -- stored lower-case
    password_hash       varchar(100) NOT NULL,          -- BCrypt, strength 12
    public_phone_enc    text         NOT NULL,
    emergency_phone_enc text         NOT NULL,
    address             varchar(300),
    location            geography(Point, 4326) NOT NULL, -- an institution's own place, not a person's
    status              varchar(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    status_reason       varchar(300),
    verified_at         timestamptz,
    verified_by         uuid,
    created_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_institutions_email   ON institutions (lower(email));
CREATE UNIQUE INDEX uq_institutions_licence ON institutions (upper(licence_number));
CREATE INDEX idx_institutions_location ON institutions USING GIST (location);
CREATE INDEX idx_institutions_status   ON institutions (status);

-- ADMIN accounts. Created only at startup from environment settings.
CREATE TABLE admins (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_admins_email ON admins (lower(email));

-- One-time code challenges. The code itself is never stored, only a keyed hash.
CREATE TABLE otp_challenges (
    id              uuid PRIMARY KEY,
    purpose         varchar(30) NOT NULL
                    CHECK (purpose IN ('USER_LOGIN', 'INSTITUTION_STEP2', 'ADMIN_STEP2')),
    phone_index     text,          -- USER_LOGIN only
    phone_enc       text,          -- USER_LOGIN only; cleared as soon as the challenge is used
    principal_id    uuid,          -- INSTITUTION_STEP2 / ADMIN_STEP2 only
    code_hash       text        NOT NULL,
    expires_at      timestamptz NOT NULL,
    failed_attempts int         NOT NULL DEFAULT 0,
    consumed_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone     ON otp_challenges (phone_index, created_at);
CREATE INDEX idx_otp_principal ON otp_challenges (principal_id, created_at);

-- Login tokens. Only the SHA-256 hash of a token is stored.
CREATE TABLE auth_tokens (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash     char(64)    NOT NULL UNIQUE,
    principal_type varchar(20) NOT NULL CHECK (principal_type IN ('USER', 'INSTITUTION', 'ADMIN')),
    principal_id   uuid        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz
);
CREATE INDEX idx_tokens_principal ON auth_tokens (principal_type, principal_id);

-- Audit log. Holds ids and action names only. Never phone numbers, emails, codes, tokens
-- or coordinates. Needs review before real use: how long should audit rows be kept?
CREATE TABLE audit_log (
    id          bigserial PRIMARY KEY,
    at          timestamptz NOT NULL DEFAULT now(),
    actor_type  varchar(20) NOT NULL,
    actor_id    uuid,
    action      varchar(60) NOT NULL,
    target_type varchar(30),
    target_id   uuid,
    detail      varchar(300)
);
CREATE INDEX idx_audit_at    ON audit_log (at DESC);
CREATE INDEX idx_audit_actor ON audit_log (actor_id);
