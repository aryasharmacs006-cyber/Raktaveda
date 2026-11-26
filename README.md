# Raktaveda

Raktaveda connects people who need blood with blood donors, hospitals and blood banks. A family can post an emergency request, nearby compatible donors are alerted, and hospitals and blood banks can be found on a map with their phone numbers and directions.

> **Status:** College project, built for demonstration. It is not yet in real use. Medical, legal and privacy wording is marked for review before any real launch. All seed data is labelled **TEST DATA** and uses fictional 555 phone numbers.

## Features

- **Donors and families:** phone + one-time code login, donor profile, eligibility estimate, "Available now" with location.
- **Emergency requests:** blood group, component, units, urgency and location. Compatible donors are counted within 5, 10 and 25 km.
- **Donor responses:** donors accept or decline. A requester sees a masked contact until the donor accepts.
- **Find blood:** hospitals and blood banks near you on a map, sorted by distance, with call and directions buttons.
- **Institutions:** registration with licence details, email and password login with a second-step code, and verification by an administrator.
- **Chat:** real-time messages (WebSocket) between a user and a verified institution, with reporting.
- **Rakta assistant:** short answers about donating and using the app. Phone numbers and emails are removed from questions, and questions and answers are not stored.
- **Account deletion:** two confirmations before personal data is removed.
- **Admin:** verify, reject or suspend institutions, review reported messages, view the audit log, import official institution data (CSV, with dry-run).
- **Organ donation:** information page that points to the official national portal.
- **Languages:** English, Hindi, Korean, Simplified Chinese and Spanish.

## Live links

- **Website:** https://aryasharmacs006-cyber.github.io/Raktaveda/
- **API documentation (Swagger):** runs locally at `http://localhost:8080/swagger-ui.html`

The backend needs a database, a secret key and a server. It is not deployed anywhere yet, so the website cannot reach it from the internet.

## Tech stack

**Backend** (`backend/`)
- Java 21, Spring Boot 3.3.5
- Spring Web, Spring Security, Spring JDBC (JdbcTemplate, no JPA), Spring Validation, Spring Actuator, Spring WebSocket (STOMP)
- PostgreSQL 16 with PostGIS (nearby search)
- Flyway (database migrations)
- BCrypt (passwords), AES-GCM (phone numbers and dates of birth)
- In-memory rate limiter
- springdoc-openapi (Swagger UI)
- Maven, Docker Compose (database)

**Frontend** (`frontend/`)
- One HTML file (`index.html`) with HTML, CSS and JavaScript. No build step.
- Leaflet 1.9.4 (maps), CARTO map tiles with Esri fallback
- @stomp/stompjs 7.0.0 (live chat)
- Google Fonts (Fraunces, Plus Jakarta Sans)

## Project structure

```
raktaveda/
├── backend/          Spring Boot API (Java 21)
│   ├── src/
│   ├── docs/         API.md and SECURITY.md
│   ├── docker-compose.yml
│   └── .env.example
├── frontend/
│   └── index.html    The whole website
└── README.md
```

## Run the backend

1. Copy `backend/.env.example` to `backend/.env` and fill in the values:
   - `RAKTAVEDA_DB_PASSWORD`: any password for the local database
   - `RAKTAVEDA_DATA_KEY`: a secret key for encrypting phone numbers (keep it private)
   - `OTP_DEV_MODE=true`: shows login codes on screen instead of sending SMS (development only)
   - `RAKTAVEDA_SEED_TEST_DATA=true`: loads TEST DATA donors and hospitals
   - `RAKTAVEDA_ADMIN_EMAIL` and `RAKTAVEDA_ADMIN_PASSWORD`: the administrator account (password 12+ characters)
   - `AI_API_KEY` (optional): without it, the assistant uses a built-in fallback
2. Start the database:
   ```bash
   cd backend
   docker compose up -d
   ```
3. Start the backend from IntelliJ, or with `mvn spring-boot:run`. It runs on port **8080**.

Never commit `.env`. It contains secrets.

## Run the website

1. Keep the backend running.
2. In a terminal, go to the `frontend/` folder and run:
   ```bash
   python3 -m http.server 3000
   ```
3. Open **http://localhost:3000** in Chrome.

Do not open `index.html` by double-clicking it. The browser then blocks the connection to the backend and the map.

To point the website at a different backend, set `window.RAKTAVEDA_API` in `index.html`, or run `localStorage.setItem('rv_api', 'https://your-server')` in the browser console.

## Test logins

| Role | How to log in |
|---|---|
| Donor or family | Phone `+15555550121` (TEST DATA donor). The code appears on screen in development mode. |
| Administrator | The email and password you set in `.env`. |
| Hospital or blood bank | Register on the site, then the administrator verifies it. The seeded test hospitals have no login. |

## Before real use

- Eligibility values are estimates and must be checked against current NBTC and MoHFW guidelines.
- Privacy policy and terms of use need legal review.
- Organ donation links must be checked against the official portal before launch.
- Set `OTP_DEV_MODE=false`, `RAKTAVEDA_SEED_TEST_DATA=false` and use HTTPS.

Full details: `backend/docs/API.md` and `backend/docs/SECURITY.md`.

## Licence

Academic project. Add a licence before sharing it publicly beyond your college.
