-- Stage 2: donor profile.
-- One row per user. The row is deleted automatically if the user account is deleted.

CREATE TABLE donor_profiles (
    user_id               uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    blood_group           varchar(3)   NOT NULL
                          CHECK (blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    dob_enc               text         NOT NULL,           -- date of birth, encrypted with AES-GCM
    weight_kg             numeric(4,1) NOT NULL CHECK (weight_kg BETWEEN 20 AND 300),
    last_donation_date    date,                            -- Needs review before real use: health-related, stored in plain form
    available             boolean      NOT NULL DEFAULT false,
    -- Only the CURRENT location, rounded to about 100 m. Never a history.
    location              geography(Point, 4326),
    location_updated_at   timestamptz,
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    -- A location can exist only while "available now" is on.
    CONSTRAINT chk_location_only_when_available CHECK (available OR location IS NULL)
);

-- Used by nearby-donor matching in Stage 3. Partial index: only donors who are available now.
CREATE INDEX idx_donor_available_location ON donor_profiles USING GIST (location) WHERE available;
