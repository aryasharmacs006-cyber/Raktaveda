-- Stage 5: push notifications, transport flag.

-- Transport flag: an institution says "we need transport arranged through our own channels".
-- This is only a flag. Raktaveda does no delivery logistics.
ALTER TABLE blood_requests ADD COLUMN needs_transport boolean NOT NULL DEFAULT false;

-- Phone push tokens (the address Firebase Cloud Messaging uses to reach one phone).
-- token_enc : the token, encrypted with AES-GCM, because the server must read it back to send a push.
-- token_index: keyed hash of the token, so the same token cannot be stored twice.
-- A token is deleted when the account is deleted.
CREATE TABLE device_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_enc   text        NOT NULL,
    token_index text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- The development notifier only writes a row here to show that "a notification was sent".
-- It holds NO personal data: no user, no token, no request, no text.
CREATE TABLE notification_log (
    id      bigserial PRIMARY KEY,
    kind    varchar(40) NOT NULL,
    sent_at timestamptz NOT NULL DEFAULT now()
);
