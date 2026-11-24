ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS transport_needed boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS device_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_enc text NOT NULL,
  token_index char(64) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notification_log (
  id bigserial PRIMARY KEY,
  sent_at timestamptz NOT NULL DEFAULT now(),
  channel varchar(16) NOT NULL,
  message_key varchar(60) NOT NULL,
  recipient_count int NOT NULL
);
