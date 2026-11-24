-- Stage 4: chat between a user and a verified institution, and message reports.

-- One conversation per (user, institution) pair. Only a user can start one, so an institution
-- never learns who the user is (no phone, no id shown to it beyond this row).
CREATE TABLE conversations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    institution_id  uuid        NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    last_message_at timestamptz,
    UNIQUE (user_id, institution_id)
);
CREATE INDEX idx_conversations_institution ON conversations (institution_id, last_message_at DESC);

-- Message text is encrypted (AES-GCM) so a copy of the database does not expose conversations.
-- Needs review before real use: how long chat messages are kept (there is no automatic deletion yet).
CREATE TABLE chat_messages (
    id              bigserial PRIMARY KEY,
    conversation_id uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_type     varchar(11) NOT NULL CHECK (sender_type IN ('USER', 'INSTITUTION')),
    body_enc        text        NOT NULL,
    sent_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, id DESC);

CREATE TABLE chat_reports (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      bigint      NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    conversation_id uuid        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    reporter_type   varchar(11) NOT NULL CHECK (reporter_type IN ('USER', 'INSTITUTION')),
    reporter_id     uuid        NOT NULL,
    reason          varchar(20) NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'HARASSMENT', 'MISINFORMATION', 'OTHER')),
    note            varchar(300),
    status          varchar(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DISMISSED', 'ACTIONED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    resolved_at     timestamptz,
    resolved_by     uuid,
    resolution_note varchar(300),
    UNIQUE (message_id, reporter_type, reporter_id)   -- reporting the same message twice changes nothing
);
CREATE INDEX idx_chat_reports_status ON chat_reports (status, created_at);
