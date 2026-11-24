# Raktaveda API

Base URL in development: `http://localhost:8080`. All bodies are JSON unless noted. Live, browsable docs (Swagger) are at `/swagger-ui.html` when `RAKTAVEDA_API_DOCS_ENABLED=true`.

## 1. Basics

**Authentication.** After login you get a `token`. Send it on every call: `Authorization: Bearer <token>`.
Token lifetimes: user 168 h, institution 12 h, admin 2 h. `POST /api/auth/logout` revokes it.

**Roles.** `USER` (donor or someone asking for blood), `INSTITUTION` (registered, not yet checked), `INSTITUTION_VERIFIED` (checked by an admin), `ADMIN`.

**Errors.** Every error has the same shape. `message` is a KEY, not display text. The frontend translates it (en, hi, ko, zh-CN, es).

```json
{ "error": "OTP_INVALID", "message": "error.otp.invalid", "timestamp": "2026-01-01T10:00:00Z" }
```

Status codes: 400 validation, 401 not logged in or wrong code, 403 not allowed, 404 not found, 409 conflict, 413 file too large, 422 import blocked, 429 rate limited, 503 SMS or email not set up.

**Rate limits** (per address, in memory, one server only): auth 10/min, search 60/min, general 300/min. Chat limits are per account.

## 2. Endpoints

### Auth (`/api/auth`, public unless noted)
| Method | Path | Purpose |
|---|---|---|
| POST | /user/otp/request | Send a login code by SMS to a phone. Body: `{phone}` |
| POST | /user/otp/verify | Body: `{challengeId, code}`. Returns a user token. Creates the account on first login |
| POST | /institution/register | Register an institution (starts PENDING) |
| POST | /institution/login | Step 1: email and password. Sends a code by email |
| POST | /admin/login | Step 1 for the admin. Sends a code by email |
| POST | /second-step/verify | Step 2: `{challengeId, code}`. Returns the token |
| POST | /logout | Needs token. Revokes it |
| GET | /me | Needs token. Who am I |

In development (`OTP_DEV_MODE=true`) the code is returned in the response as `devCode`. In production it never is.

### Donor (`/api/donor`, role USER)
| Method | Path | Purpose |
|---|---|---|
| GET / PUT | /profile | Blood group, date of birth, weight, last donation date |
| GET | /eligibility | Estimate only. The blood bank decides |
| PUT | /availability | `{available, latitude, longitude}`. Location is rounded to about 100 m, erased when switched off, and expires after 12 h |
| GET | /requests | Open requests that match this donor (group, distance, eligibility) |
| POST | /requests/{id}/accept | Accept. The requester can now see the donor's phone |
| POST | /requests/{id}/decline | Decline |
| POST | /device-token | Save a push token. Body: `{token}` |
| POST | /device-token/unregister | Remove it |

### Emergency requests (`/api/requests`, role USER or verified institution)
| Method | Path | Purpose |
|---|---|---|
| POST | / | Create. Blood group, component, units, urgency, location |
| GET | /mine | My requests |
| GET | /{id} | One request, with accepted donors (phone shown only while OPEN) |
| POST | /{id}/cancel | Close it. Its location is erased |
| PUT | /{id}/transport | `{needsTransport}` |
| POST | /{id}/responses/{responseId}/confirm | Confirm a donor did donate |

Limits: 5 new requests per hour, 3 open at once, 10 accepting donors per request, expiry after 48 h.

### Chat (`/api/chat`, user or VERIFIED institution)
| Method | Path | Purpose |
|---|---|---|
| POST | /conversations | Start with a verified institution |
| GET | /conversations | My conversations |
| GET | /conversations/{id}/messages | Read messages |
| POST | /conversations/{id}/messages | Send (plain text) |
| POST | /messages/{messageId}/report | Report a message |

Imported institutions have no login, so they cannot be chatted with.

### Account (`/api/account/delete`)
| Method | Path | Purpose |
|---|---|---|
| POST | /start | Step 1. A user gets an SMS code. An institution sends `{password}` and gets an email code |
| POST | /confirm | Step 2. `{challengeId, code, confirmation: "DELETE"}`. Deletes everything |

Admins cannot delete themselves here.

### Admin (`/api/admin`, role ADMIN)
| Method | Path | Purpose |
|---|---|---|
| GET | /institutions?status= | List, with `source` (REGISTERED or IMPORTED) |
| POST | /institutions/{id}/verify, /reject, /suspend | Change status (reason required for reject and suspend) |
| GET | /reports, /reports/{id} | Chat reports. Opening one is written to the audit log |
| POST | /reports/{id}/resolve | Close a report |
| GET | /audit | Audit log |
| POST | /import/institutions | CSV import (below) |

### AI assistant (`/api/v1/assistant`, role USER or INSTITUTION)
| Method | Path | Purpose |
|---|---|---|
| POST | /ask | General questions about blood donation and the app. Body: `{"question": "..."}` |

Response: `{"answer": "...", "source": "AI" | "SAFETY" | "FALLBACK", "remainingToday": 19}`.
- `AI`: written by the AI from the approved facts. A note that the blood bank or a doctor decides eligibility is always added.
- `SAFETY`: a fixed reply, the AI was not called (emergency words, dosage or diagnosis request, or self-harm).
- `FALLBACK`: the AI's reply failed a safety check, so a fixed reply was used.

The reply language follows the question (English, Hindi, Korean, Simplified Chinese, Spanish; otherwise English).
Limits: 300 characters per question, 5 per minute and 20 per day per account (day = UTC day).
Error codes: `ASSISTANT_QUESTION_REQUIRED` (400), `ASSISTANT_QUESTION_TOO_LONG` (400), `ASSISTANT_RATE_LIMITED` (429), `ASSISTANT_DAILY_LIMIT` (429), `ASSISTANT_UNAVAILABLE` (503, the AI service failed or took more than 10 seconds).
Message keys: `error.assistant.question_required`, `question_too_long`, `rate_limited`, `daily_limit`, `unavailable`.
Admins get 403. The assistant reads and changes no donor, request, stock or chat data.
Without `AI_API_KEY` a fake assistant with one fixed reply is used.

### Nearby hospitals and blood banks (map data) (`/api/search`, role USER or INSTITUTION)
`GET /api/search/institutions/nearby?lat=23.2599&lon=77.4126&radiusKm=10&type=BLOOD_BANK&limit=20`

Only `lat` and `lon` are required. `radiusKm` is 1 to 50 (default 10), `type` is HOSPITAL or BLOOD_BANK (default both), `limit` is 1 to 50 (default 20).
Returns VERIFIED institutions, nearest first, ready for map pins:
`[{"id","name","type","address","publicPhone","latitude","longitude","distanceKm"}]`.
Errors: `LOCATION_INVALID`, `RADIUS_INVALID`, `TYPE_INVALID`, `VALIDATION_FAILED` (all 400). Message keys: `error.search.location_invalid`, `radius_invalid`, `type_invalid`. The search rate limit (60 per minute) applies.
The point you send is not stored. With `RAKTAVEDA_SEED_TEST_DATA=true` the list contains 6 made-up TEST DATA places near Bhopal.

### CSV import
`POST /api/admin/import/institutions`, multipart form: `file` (CSV) and `dryRun` (default `true`).

Columns: `name,type,licence_number,public_phone,emergency_phone,latitude,longitude` and optional `address`. `type` is HOSPITAL or BLOOD_BANK. First row is the header. UTF-8. Max 1 MB and 2000 rows. Cells starting with `=`, `+`, `-` or `@` are rejected (spreadsheet formula attack).

Result: `{dryRun, outcomeKey, totalRows, newRows, duplicateRows, invalidRows, importedRows, rows:[{row,status,errors}]}`.
- 200 `import.outcome.dry_run`: nothing saved.
- 201 `import.outcome.imported`: saved. Duplicates (same licence, or same name within 100 m) are skipped.
- 422 `import.outcome.blocked_by_invalid_rows`: one bad row blocks everything. Fix and retry.

Row error keys: `import.row.name_invalid`, `type_invalid`, `licence_invalid`, `public_phone_invalid`, `emergency_phone_invalid`, `address_invalid`, `latitude_invalid`, `longitude_invalid`. Row status: NEW, DUPLICATE, INVALID.
Imported institutions are saved as VERIFIED with source IMPORTED and no login.

## 3. WebSocket chat
Connect to `/ws` (STOMP). Put the token in the CONNECT frame header `Authorization: Bearer <token>`.
- Subscribe: `/user/queue/messages` (new messages) and `/user/queue/errors`. Other subscriptions are refused.
- Send to `/app/chat.send` with `{conversationId, body}`. Other destinations are refused.
- The token is re-checked on every frame, so logout stops the socket.

## 4. Push notifications
Pushes carry only a message key and the request id (never names, phones or places). Provider is set by `RAKTAVEDA_PUSH_PROVIDER` (`dev` records only). Max 5 alerts per donor per hour.

## 5. Message keys the frontend must translate
Errors: `error.validation.failed`, `error.rate_limited`, `error.auth.*` (`invalid_credentials`, `locked`), `error.otp.*` (`invalid`, `expired`, `locked`, `rate_limited`), `error.phone.invalid`, `error.sms.not_configured`, `error.email.not_configured`, `error.institution.exists`, `error.profile.*` (`not_found`, `required`, `dob_invalid`, `last_donation_invalid`), `error.donor.*` (`not_eligible`, `availability_required`), `error.availability.location_required`, `error.request.*` (`not_found`, `closed`, `full`, `rate_limited`, `too_many_open`, `location_required`, `location_invalid`, `already_donated`, `response_not_accepted`), `error.chat.*` (`conversation_not_found`, `institution_not_found`, `institution_not_verified`, `message_not_found`, `cannot_report_own`, `rate_limited`), `error.admin.*` (`institution_not_found`, `invalid_status_change`, `reason_required`, `report_not_found`, `report_already_resolved`), `error.account.*` (`not_found`, `admin_not_deletable`), `error.import.*` (`conflict`, `malformed`, `too_large`), `error.assistant.*` (listed above).
Import: `import.outcome.*` and `import.row.*` (listed above). Eligibility and notification keys are `eligibility.*` and `notification.*` (see `EligibilityService` and `NotificationService` for the full list).

## 6. Not built yet
Live blood stock, help board, organ donation information, institution dashboards, and a real SMS, email and push provider (only the interfaces and dev versions exist).
