# Deploy to Render with Neon Postgres — Design Spec

**Date:** 2026-04-19

## Goal

Deploy the Lista AI Spring Boot service to Render (free tier) using Neon Postgres as the managed database, with automatic deploys triggered from CI after tests pass.

## Architecture

```
GitHub push to main
  └─ GitHub Actions CI
       ├─ Build & test (existing)
       └─ On success: curl Render deploy hook
                          └─ Render builds Docker image
                               └─ Runs container → Neon Postgres (SSL)
```

## Components

### 1. Dockerfile (multi-stage)

**Build stage** (`eclipse-temurin:25-jdk`):
- Copies source and Gradle wrapper
- Runs `./gradlew build -x test` (tests already run in CI)
- Produces `build/libs/*.jar`

**Runtime stage** (`eclipse-temurin:25-jre`):
- Copies JAR from build stage
- `EXPOSE 8080` (metadata only — app binds to whatever `SERVER_PORT` is set to)
- `ENTRYPOINT ["java", "-jar", "app.jar"]`

Rationale: Java 25 is not available in Render's native buildpacks, so Docker is required. Multi-stage keeps the runtime image lean, improving cold start time on the free tier.

### 2. `render.yaml`

Declarative service definition committed to the repo:

- **type**: `web`, **runtime**: `docker`
- **region**: `oregon`
- **plan**: `free`
- **healthCheckPath**: `/actuator/health`
- **env vars** (all `sync: false` — values entered in Render dashboard, never stored in file):
  - `DB_URL` — Neon JDBC URL: `jdbc:postgresql://<host>/lista_ai_db?sslmode=require`
  - `DB_USERNAME`, `DB_PASSWORD` — Neon credentials
  - `JWT_SECRET`
  - `GOOGLE_CLIENT_ID`
  - `SERVER_PORT` — set to `10000` (Render free tier injects `PORT=10000`)
  - `RESEND_API_KEY` — Resend API key
  - `EMAIL_FROM` — from-address (e.g. `noreply@lista-ai.com`)
  - `EMAIL_VERIFICATION_ENABLED` — `"true"` in prod, `"false"` in tests/dev
  - `VERIFICATION_REDIRECT_BASE_URL` — `https://app.lista-ai.com/verify-email`

No changes to `application.yaml` are needed — it already reads all connection config from env vars.

### 3. CI Deploy Step

Added to `.github/workflows/ci.yml`, runs only on `main` push after a successful build:

```yaml
- name: Deploy to Render
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  run: curl -s "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
```

New GitHub secret required: `RENDER_DEPLOY_HOOK_URL` (generated from the Render service dashboard after initial setup).

## Neon Postgres

Neon requires SSL. The JDBC URL must include `?sslmode=require`. This is set via the `DB_URL` env var in Render — no code changes needed since the app already parameterises the datasource URL.

## Manual Setup Steps (one-time, outside the codebase)

These cannot be automated via code:

1. Create a Neon project and database; copy the connection string
2. Create the Render service pointing at this repo (Render reads `render.yaml` automatically)
3. Set env var values in the Render dashboard
4. Copy the Render deploy hook URL into GitHub secrets as `RENDER_DEPLOY_HOOK_URL`

## Free Tier Behavior

Render free tier spins down after 15 minutes of inactivity. Cold starts take ~30 seconds. This is acceptable for the current stage of the project.

## Files Changed

| File | Change |
|---|---|
| `Dockerfile` | New — multi-stage build |
| `render.yaml` | New — service definition |
| `.github/workflows/ci.yml` | Add deploy step |
