# Deployment Guide

## Prerequisites

- JDK 21 (Temurin recommended)
- Docker + Docker Compose (for local infrastructure)
- An RSA-2048 key pair for JWT signing

---

## 1. First-time setup

### 1.1 Generate RSA key pair

```bash
mkdir -p src/main/resources/keys
openssl genrsa -out src/main/resources/keys/private.pem 2048
openssl rsa -in src/main/resources/keys/private.pem -pubout \
            -out src/main/resources/keys/public.pem
```

> `private.pem` is git-ignored. `public.pem` can be committed (it is public).

### 1.2 Configure environment

```bash
cp .env.example .env
# Edit .env with your values
```

---

## 2. Local development

### 2.1 Start infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL (5432), Redis (6379), MinIO (9000/9001), and MailDev (1025/1080).

### 2.2 Run the application

```bash
# Windows
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or set the env var and run
set SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

Swagger UI is available at <http://localhost:8080/swagger-ui.html>.
MailDev web UI is at <http://localhost:1080>.
MinIO console is at <http://localhost:9001> (minioadmin / minioadmin).

### 2.3 Compile-only (no Docker required)

```bash
./mvnw clean package -DskipTests
```

---

## 3. Running tests

### Unit tests

```bash
./mvnw test
```

### Integration tests (Testcontainers — requires Docker)

```bash
./mvnw verify
```

Testcontainers spins up throwaway PostgreSQL and Redis containers automatically.

---

## 4. Building the Docker image

```bash
./mvnw clean package -DskipTests
docker build -t shop-backend:latest .
```

The image uses a two-stage build (JDK builder → JRE runtime) with layered JAR
extraction for optimal Docker layer caching.

---

## 5. Production deployment

### 5.1 Environment variables

All secrets are injected via environment variables at runtime (see `.env.example`
for the full list). Never bake secrets into the image.

Mandatory in production:
- `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PASSWORD`
- `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION`
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
- `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`
- `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`
- `SSLCOMMERZ_STORE_ID`, `SSLCOMMERZ_STORE_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `APPLE_CLIENT_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`

### 5.2 Mount RSA keys at runtime

Do not put the private key inside the image. Mount it as a secret or volume:

```bash
docker run -d \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=... \
  -v /run/secrets/private.pem:/app/keys/private.pem:ro \
  -e JWT_PRIVATE_KEY_LOCATION=file:/app/keys/private.pem \
  -p 8080:8080 \
  shop-backend:latest
```

### 5.3 Health checks

| Probe      | URL                                      |
|------------|------------------------------------------|
| Liveness   | `GET /actuator/health/liveness`          |
| Readiness  | `GET /actuator/health/readiness`         |
| Full health| `GET /actuator/health` (auth required)   |
| Metrics    | `GET /actuator/metrics`                  |
| Prometheus | `GET /actuator/prometheus`               |

---

## 6. Flyway migrations

Migrations live in `src/main/resources/db/migration/` following the naming convention:

```
V<version>__<description>.sql
```

Example: `V1__init_schema.sql`, `V2__add_product_images.sql`

Dev-only seed data lives in `src/main/resources/db/seed/` and is loaded only when
the `dev` profile is active (via `spring.flyway.locations` override in
`application-dev.yaml`).

---

## 7. Useful commands

```bash
# Check dependency tree
./mvnw dependency:tree

# Resolve all dependencies (populate local .m2 cache)
./mvnw dependency:go-offline

# Run with a specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# View Flyway migration status
./mvnw flyway:info
```
