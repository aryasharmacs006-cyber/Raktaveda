# Raktaveda security notes

Honest status: this is a college-project backend. It has **not** had a security audit. Read the checklist before putting it on the internet with real people's data.

## What is protected, and how
- **Phone numbers** are encrypted with AES-GCM. Each field is bound to its place (e.g. `users.phone`), so a value copied to another column will not decrypt. Lookups use a keyed hash (blind index), so the database never needs the plain phone to find someone.
- **Keys.** One master key (`RAKTAVEDA_DATA_KEY`, 32 bytes base64). Separate keys for encryption, lookup hashes and code hashes are derived from it. Lose the key and every phone is unreadable.
- **Login codes (OTP)** are stored only as hashes tied to the challenge, expire in 5 min, allow 3 wrong tries, and 3 requests per phone per 10 min. A login code cannot confirm an account deletion (separate purposes).
- **Login tokens** are 32 random bytes. Only a SHA-256 hash is stored. They expire and can be revoked.
- **Institutions and admins** need password plus an emailed code. Passwords are BCrypt (12 to 72 characters). Admin exists only through environment settings.
- **Roles.** Only admin-verified institutions can chat, and imported institutions (no login) cannot.
- **Chat messages** are encrypted at rest. WebSocket tokens are re-checked on every frame.
- **Location.** Rounded to about 100 m, erased when "available" is switched off, auto-expires after 12 h, request location erased when the request closes.
- **Phone reveal.** A donor's phone is shown only after that donor accepts and only while the request is open.
- **Notifications** carry only a key and a request id.
- **Account deletion** needs a fresh code plus the word DELETE, and removes the account, profile, tokens, requests, responses and chats.
- **Rate limiting** on all calls, stricter on auth and chat.
- **Errors** never reveal stack traces. CORS only allows configured origins. CSV import rejects formula cells, oversize files and bad encoding.
- **Audit log** records admin and account actions with random ids only.

## Keys and secrets
1. Generate: `openssl rand -base64 32`. Keep it in a secret manager, not in git.
2. Back it up separately from the database backup.
3. There is no key rotation yet (needs review, see below).

## Go-live checklist
- [ ] `OTP_DEV_MODE=false` (the app refuses to start if it is true together with `RAKTAVEDA_REQUIRE_HTTPS=true`).
- [ ] `RAKTAVEDA_REQUIRE_HTTPS=true`, and HTTPS terminated by a proxy you control; set `RAKTAVEDA_FORWARD_HEADERS=native` there.
- [ ] `RAKTAVEDA_SEED_TEST_DATA=false`.
- [ ] `RAKTAVEDA_API_DOCS_ENABLED=false`.
- [ ] `RAKTAVEDA_ALLOWED_ORIGINS` set to your real site only.
- [ ] Real SMS and email senders implemented (without them login returns 503).
- [ ] Strong database password; the database port not open to the internet.
- [ ] Data key backed up and stored safely.
- [ ] Admin account created with a long unique password, and the password variable removed afterward.
- [ ] A person with medical knowledge has reviewed every item below marked medical.
- [ ] Privacy policy and consent text written, and a legal review done (health-related data, donor contact sharing, deletion).
- [ ] Database backups and a restore test.

## Register: "Needs review before real use"
Medical
- Blood compatibility (`BloodGroupCompatibility`): red cells use the standard ABO/Rh rules; every other component needs the exact same group.
- Eligibility numbers (`application.yml`: age 18 to 65, weight 45 kg, 120 days between donations). Check against current national guidelines. Eligibility is only an estimate.
- Who may confirm that a donation happened (`RequestService`).
- Blood group can be changed freely by the donor (`DonorProfileService`).

Privacy and legal
- Location rounding and the 12 h expiry (`DonorProfileService`, `RequestService`, `application.yml`).
- When the donor's phone is shown (`DonorRequestService`) and the wording shown before "accept".
- Distance rounded up to whole km (`DonorRequestService`).
- Last donation date is stored in plain form (`V2`). It is health-related.
- Chat messages are kept until the conversation is deleted. There is no automatic deletion (`V4`, `ChatService`).
- Admin can read reported messages; opening is audit-logged (`AdminService`).
- Audit rows are kept after account deletion and have no expiry (`V1`).
- Reports are deleted with the conversation, so an institution could delete itself to remove a report (`AccountDeletionService`).
- Phone numbers already shown cannot be taken back.
- Push text on a locked screen (`NotificationService`).
- Registration tells a caller that an email or licence is already taken (`InstitutionRegistrationService`).
- Imported institutions are trusted because an admin vouches for the file (`InstitutionCsvImportService`).

Security and operations
- OTP numbers, token lifetimes, password rule, chat and request limits (`OtpService`, `AuthDtos`, `application.yml`).
- Admin recovery process (`AdminBootstrap`).
- Key rotation and key backup (`CryptoService`).
- Production is recognised by `require-https=true` (`StartupChecks`); behind a proxy also set forward headers (`SecurityConfig`).
- Only Indian 10-digit numbers are auto-completed (`PhoneNumbers`).
- Donor matching uses an `age_ok` flag that can be up to 12 h out of date (`V3`).
- Test seed data (`TestDataSeeder`) must stay off on a real server.

## AI assistant module (`com.raktaveda.assistant`)
It is separate from the core app: it calls no donor, request, stock, chat or contact code.
- **Safety boundary.** The AI is told to answer only from `assistant/approved-facts.md`, never to diagnose, prescribe or give dosage, to say the blood bank or a doctor decides eligibility, to send emergencies to the emergency number, and to answer in the question's language (English, Hindi, Korean, Simplified Chinese, Spanish; otherwise English).
- **Before the AI.** Emergency, dosage/diagnosis and self-harm words (five languages) return a fixed reply and the AI is not called. Self-harm replies include helpline guidance and a note to contact local emergency services.
- **Privacy.** Phone numbers, emails and dates are replaced with `[removed]` before sending. Only the cleaned question and the approved facts are sent. No names, locations, ids, licence numbers or tokens are sent.
- **After the AI.** A reply with a phone number, or one that says the app decides eligibility (or tells a person they are or are not eligible), is replaced by a fixed reply. A note about the final decision is added to every normal answer.
- **Storage.** The question and answer are never stored or logged. The table `assistant_usage` (migration V8) keeps one row per question (account type, account id, day, time) and the daily limit (20) is counted from it. The audit log keeps only the time, account type, account id, `ASSISTANT_QUESTION` and the question length.
- **Key.** `AI_API_KEY` and `AI_MODEL` come from the environment only. The key is never logged or put in config. Calls time out after 10 seconds and fail with `ASSISTANT_UNAVAILABLE`.

Needs review before real use (medical, legal, privacy):
- A doctor must review `approved-facts.md` and the keyword lists (`SafetyFilter`).
- A doctor and native speakers must review the fixed replies (`SafeReplies`). Check 112 and the Tele-MANAS numbers (14416, 1800-891-4416).
- Decide whether sending cleaned text to an outside AI provider is allowed for your users, and read that provider's data rules. The real service is normally paid.
- Names and places typed in words are NOT removed (rules cannot find them reliably). Tell users not to type personal details.
- Keyword checks miss some phrasings and trigger on some harmless ones. The "claims eligibility" output check is written for English and Spanish only.
- The per-minute limit is in memory (one server only). The day for the daily limit is the UTC day. Questions that end in a fixed reply or an AI error still count.
- `approved-facts.md` says to use the app's search for nearby blood banks and hospitals. The backend now has `GET /api/search/institutions/nearby` (map data); check that the facts file matches how your app screen is named.

## Known limitations
- The rate limiter is in memory. It works for one server only and resets on restart. Use a shared store if you run several.
- No automatic chat or request-history cleanup beyond request retention (90 days).
- Chat is plain text only, and no spam or abuse detection beyond limits and reports.
- No two-factor for ordinary users beyond the SMS code.
- No security audit or penetration test has been done.
- The code was written without being compiled in the authoring environment. Run `./mvnw test` (or `mvn test`) first and fix anything it reports.
