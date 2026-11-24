-- Stage 3: emergency blood requests, donor responses.

-- age_ok is worked out in code (the date of birth is encrypted, so SQL cannot read it) every time
-- a donor saves their profile or turns "available now" on. Matching queries use it.
-- Needs review before real use: it can be up to the availability time limit out of date.
ALTER TABLE donor_profiles ADD COLUMN age_ok boolean NOT NULL DEFAULT false;

CREATE TABLE blood_requests (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_type varchar(20) NOT NULL CHECK (requester_type IN ('USER', 'INSTITUTION')),
    requester_id   uuid        NOT NULL,   -- not a foreign key because it points at two different tables
    blood_group    varchar(3)  NOT NULL
                   CHECK (blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    component      varchar(20) NOT NULL
                   CHECK (component IN ('WHOLE_BLOOD', 'PLATELETS', 'PLASMA', 'PACKED_RED_CELLS')),
    units          int         NOT NULL CHECK (units BETWEEN 1 AND 10),
    urgency        varchar(10) NOT NULL CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status         varchar(10) NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED', 'EXPIRED')),
    -- Where the blood is needed. Erased as soon as the request is no longer open.
    location       geography(Point, 4326),
    created_at     timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    closed_at      timestamptz,
    CONSTRAINT chk_open_has_location CHECK (status <> 'OPEN' OR location IS NOT NULL)
);
CREATE INDEX idx_requests_open_location ON blood_requests USING GIST (location) WHERE status = 'OPEN';
CREATE INDEX idx_requests_requester ON blood_requests (requester_type, requester_id, created_at DESC);
CREATE INDEX idx_requests_status_expiry ON blood_requests (status, expires_at);

CREATE TABLE request_responses (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id    uuid        NOT NULL REFERENCES blood_requests (id) ON DELETE CASCADE,
    donor_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        varchar(10) NOT NULL CHECK (status IN ('ACCEPTED', 'DECLINED', 'DONATED')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    responded_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (request_id, donor_user_id)
);
CREATE INDEX idx_responses_donor ON request_responses (donor_user_id);
