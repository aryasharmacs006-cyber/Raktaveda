-- AI assistant: one row per question asked, used ONLY to count questions per account per day.
-- No question text, no answer text, no names. Needs review before real use: how long to keep these rows
-- (there is no automatic deletion yet), and the day counts as the UTC day.
-- (The name V8 is used because V2 to V7 already exist. Flyway needs a new, higher number.)
CREATE TABLE assistant_usage (
    id           bigserial PRIMARY KEY,
    account_type varchar(20) NOT NULL CHECK (account_type IN ('USER', 'INSTITUTION')),
    account_id   uuid        NOT NULL,
    asked_on     date        NOT NULL DEFAULT ((now() AT TIME ZONE 'UTC')::date),
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_assistant_usage_account_day ON assistant_usage (account_id, asked_on);
