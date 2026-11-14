# LendLedger

A web application for independent lenders in Kenya to manage borrowers, loans, repayments, overdue follow-ups, and reports. Built to replace fragmented manual record-keeping with a centralized, auditable ledger.

**[Live Demo](https://lend-ledger-iota.vercel.app)** | Built with Spring Boot & React

## What it does

- **Borrowers:** Manage contacts, ID photos and KYC verification status.
- **Loans:** Create loans with transparent interest and penalty previews before saving.
- **Payments:** Automatic allocation to penalty, interest and principal. Includes undo/rollback with a strict audit trail.
- **Overdue tracking:** Prioritized queue with one-tap WhatsApp, call and email actions.
- **Receipts & Statements:** Shareable via the lender's own WhatsApp, SMS or email.
- **Reports & Data:** Monthly charts, CSV exports and bulk Excel imports.
- **Versioned Rules:** Lending rules are locked to the loan at creation, so historical data never changes.
- **Multi-Tenant:** Strict data isolation for every lender (each tenant's data is fully siloed).

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Frontend** | React 19, Vite, Plain CSS, SVG Charts |
| **Backend** | Spring Boot 4, Java 21, Maven |
| **Database** | PostgreSQL 18, Flyway (Migrations) |
| **Hosting** | Vercel (Frontend), Render (Backend), Neon (Database) |

## Architecture

The frontend is deployed on Vercel and uses Vercel rewrites to proxy all `/api/*` requests to the Spring Boot backend on Render. Because the browser sees a single origin (Vercel), session cookies and CSRF protection function seamlessly. The backend enforces tenant isolation on every SQL query and stores all monetary calculations in whole shillings.

Security hardening includes pessimistic database locking for concurrent payments, automated Testcontainers integration tests to prove data isolation and automated GPG-encrypted database backups.

## API Documentation

The backend exposes a fully interactive Swagger UI for testing endpoints and reviewing API contracts.

*   **Live Swagger UI:** https://lendledger-lz0e.onrender.com/swagger-ui/index.html
*   **Local Swagger UI:** http://localhost:8080/swagger-ui/index.html
*   **Static OpenAPI Spec:** Available in `docs/openapi.yaml`
## Run Locally

**Prerequisites:** Java 21+, Node 20+, and Docker.

```bash
# 1. Start the Database
cd backend
docker compose up -d

# 2. Start the Backend
./mvnw spring-boot:run

# 3. Start the Frontend (in a new terminal)
cd frontend
npm install
npm run dev
```
Open http://localhost:5173 in your browser.

## Environment Variables

Copy `.env.example` for the required variable names. Never commit real secrets.

| Platform | Variable | Purpose |
|---|---|---|
| **Render** | `SPRING_DATASOURCE_URL` | Neon database connection |
| **Render** | `SPRING_PROFILES_ACTIVE` | Set to `prod` in production |
| **Vercel** | `VITE_DEMO_WHATSAPP` | "Book a demo" WhatsApp link |
| **Vercel** | `VITE_DEMO_EMAIL` | "Book a demo" Email link |

## Project Layout

```text
backend/    Spring Boot API, Flyway migrations, Tests
frontend/   React SPA, UI components, Vite config
scripts/    Database backup and restore scripts (encrypted)
docs/       Product specification and roadmap
```
## Status

- [x] Borrower management with ID photos
- [x] Loans, payments and rollbacks
- [x] Overdue tracking & statements
- [x] CSV/Excel import & export
- [x] Multi-tenant architecture & Operator console
- [x] Automated, encrypted database backups
- [ ] M-Pesa (Daraja) STK Push integration
- [ ] Automated SMS/Email reminders

## Contact

**Susan Waithira**
- [GitHub Profile](https://github.com/sWaithira)
- [Live Application](https://lend-ledger-iota.vercel.app)
- Email: sw.nyawira@gmail.com