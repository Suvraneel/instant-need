# InstantNeed — B2B Wholesale Platform

A full-stack B2B ordering platform with a Spring Boot backend and Next.js frontend.

## Stack

| Layer    | Technology                              |
|----------|-----------------------------------------|
| Backend  | Spring Boot 4, Java 25, PostgreSQL 16   |
| Frontend | Next.js 16, React 19, Tailwind CSS 4    |
| Storage  | Local filesystem (dev) / AWS S3 (prod)  |
| Email    | SMTP via Spring Mail                    |
| Auth     | JWT (access + refresh tokens)           |

---

## Quick Start (Docker)

### 1. Clone both repositories

```bash
git clone <backend-repo-url> instant-need
git clone <frontend-repo-url> instant-need-web
```

Both directories must be siblings:
```
Github-Projects/
  instant-need/       ← backend (this repo)
  instant-need-web/   ← frontend
```

### 2. Configure environment variables

```bash
cd instant-need
cp .env.example .env
```

Open `.env` and set at minimum:
- `DB_PASSWORD` — any strong password
- `JWT_SECRET` — run `openssl rand -base64 64` to generate one

### 3. Start everything

```bash
docker compose up --build
```

| Service  | URL                        |
|----------|----------------------------|
| Frontend | http://localhost:3000       |
| Backend  | http://localhost:8080       |
| API Docs | http://localhost:8080/swagger-ui.html |

---

## Local Development (without Docker)

### Prerequisites
- Java 25 ([Eclipse Temurin](https://adoptium.net))
- Node.js 22
- PostgreSQL 16

### Backend

```bash
# Start PostgreSQL, then:
./mvnw spring-boot:run
```

### Frontend

```bash
cd ../instant-need-web
cp .env.example .env.local
npm install
npm run dev
```

---

## Environment Variables

See [`.env.example`](.env.example) for the full list with descriptions.

Key variables:

| Variable          | Default              | Description                        |
|-------------------|----------------------|------------------------------------|
| `DB_PASSWORD`     | —                    | PostgreSQL password (required)     |
| `JWT_SECRET`      | —                    | Base64 JWT signing key (required)  |
| `CORS_ORIGINS`    | `http://localhost:3000` | Allowed frontend origins        |
| `MAIL_ENABLED`    | `false`              | Set `true` to send real emails     |
| `STORAGE_TYPE`    | `local`              | `local` or `s3`                    |
| `STORAGE_BASE_URL`| `http://localhost:8080/uploads` | CDN/S3 public URL    |

### Production (S3 storage)

Add to `.env`:
```
STORAGE_TYPE=s3
S3_BUCKET=instantneed-products-images
AWS_REGION=ap-northeast-2
STORAGE_BASE_URL=https://<your-cloudfront-domain>.cloudfront.net
AWS_ACCESS_KEY_ID=<your-key>
AWS_SECRET_ACCESS_KEY=<your-secret>
```

---

## Running Tests

```bash
# Backend (169 tests)
./mvnw test

# Frontend (13 tests)
cd ../instant-need-web && npm test
```

---

## Project Structure

```
instant-need/
  src/main/java/com/b2b/instantneed/
    auth/           # Registration, login, JWT, password reset
    catalog/        # Categories, products, pricing tiers
    cart/           # Cart management
    order/          # Order placement, history, status
    customer/       # Customer profile, addresses
    admin/          # Admin endpoints (products, orders, customers, reports)
    common/         # Security, storage, email, exceptions, config
  src/main/resources/
    db/migration/   # Flyway SQL migrations
    application.properties

instant-need-web/
  src/
    app/            # Next.js App Router pages
    components/     # UI components (admin, catalog, cart, shared)
    lib/            # API clients, hooks, types, validation
```
