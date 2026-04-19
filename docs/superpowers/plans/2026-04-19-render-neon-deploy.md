# Render + Neon Postgres Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy Lista AI to Render (free tier) using Neon Postgres, with CI auto-deploy after tests pass.

**Architecture:** A multi-stage Dockerfile builds the JAR with JDK 25 and runs it on a slim JRE 25 image. Render hosts the service, reading all secrets from env vars. GitHub Actions triggers a Render deploy hook only after a successful build on `main`.

**Tech Stack:** Docker (eclipse-temurin:25-jdk / eclipse-temurin:25-jre), Render free tier, Neon Postgres (SSL), GitHub Actions

---

## File Map

| File | Action |
|---|---|
| `Dockerfile` | Create — multi-stage build + runtime |
| `render.yaml` | Create — Render service definition (IaC) |
| `.github/workflows/ci.yml` | Modify — add deploy step after build |

---

### Task 1: Create the Dockerfile

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Create the Dockerfile**

Create `Dockerfile` at the project root with the following content:

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src/ src/
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Notes:
- `-x test` skips tests — they already ran in CI before triggering this build
- `--no-daemon` is required inside Docker (the Gradle daemon doesn't work in containers)
- `EXPOSE 8080` is metadata only; the app binds to `SERVER_PORT` env var (set to `10000` in `render.yaml`)

- [ ] **Step 2: Verify the Docker build locally (optional but recommended)**

```bash
docker build -t lista-ai .
```

Expected: build completes successfully, final image is produced. If Docker is not available locally, skip this step — the CI/Render build will catch issues.

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "feat: add multi-stage Dockerfile for Render deployment"
```

---

### Task 2: Create `render.yaml`

**Files:**
- Create: `render.yaml`

- [ ] **Step 1: Create `render.yaml` at the project root**

```yaml
services:
  - type: web
    name: lista-ai
    runtime: docker
    region: oregon
    plan: free
    healthCheckPath: /actuator/health
    envVars:
      - key: DB_URL
        sync: false
      - key: DB_USERNAME
        sync: false
      - key: DB_PASSWORD
        sync: false
      - key: JWT_SECRET
        sync: false
      - key: GOOGLE_CLIENT_ID
        sync: false
      - key: SERVER_PORT
        value: "10000"
```

Notes on each env var:
- `DB_URL` — full Neon JDBC URL, e.g. `jdbc:postgresql://<neon-host>/lista_ai_db?sslmode=require`. The `?sslmode=require` suffix is mandatory for Neon.
- `DB_USERNAME` / `DB_PASSWORD` — Neon database credentials
- `JWT_SECRET` — same secret used in CI (must match the value in GitHub secrets)
- `GOOGLE_CLIENT_ID` — Google OAuth client ID (can be left empty if not using OAuth)
- `SERVER_PORT: "10000"` — Render free tier binds on port 10000; Spring Boot reads this as `server.port`

All `sync: false` vars must be entered manually in the Render dashboard after the service is created — they are intentionally absent from this file to avoid committing secrets.

- [ ] **Step 2: Verify the YAML is valid**

```bash
python3 -c "import yaml; yaml.safe_load(open('render.yaml'))" && echo "valid"
```

Expected output: `valid`

- [ ] **Step 3: Commit**

```bash
git add render.yaml
git commit -m "feat: add render.yaml for Render service definition"
```

---

### Task 3: Add CI Deploy Step

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Open `.github/workflows/ci.yml` and append the deploy step**

The current file ends with the Codecov upload step. Add the deploy step **after** it, still inside the `steps:` block of the `build` job:

```yaml
      - name: Deploy to Render
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: curl -s "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
```

The complete updated `ci.yml` should look like:

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build and test
        run: ./gradlew build
        env:
          JWT_SECRET: ${{ secrets.JWT_SECRET }}

      - name: Upload coverage to Codecov
        if: always()
        uses: codecov/codecov-action@v5
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: build/reports/jacoco/test/jacocoTestReport.xml
          fail_ci_if_error: false

      - name: Deploy to Render
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: curl -s "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: trigger Render deploy on successful main build"
```

---

## Manual Steps (after merging to main)

These steps happen in external dashboards — they cannot be automated via code:

1. **Create a Neon project**: Go to [neon.tech](https://neon.tech), create a project and database named `lista_ai_db`. Copy the connection string — it looks like `postgresql://<user>:<password>@<host>/lista_ai_db?sslmode=require`. Convert it to JDBC format: `jdbc:postgresql://<host>/lista_ai_db?sslmode=require`.

2. **Create the Render service**: Go to [render.com](https://render.com), click "New Web Service", connect the GitHub repo. Render will detect `render.yaml` automatically.

3. **Set env vars in Render dashboard**: For each `sync: false` var in `render.yaml`, enter the value in the Render service's "Environment" tab:
   - `DB_URL` → JDBC URL from step 1
   - `DB_USERNAME` → Neon username
   - `DB_PASSWORD` → Neon password
   - `JWT_SECRET` → same value as the GitHub `JWT_SECRET` secret
   - `GOOGLE_CLIENT_ID` → your Google OAuth client ID (or leave empty)

4. **Get the Render deploy hook URL**: In the Render service dashboard → Settings → Deploy Hook. Copy the URL.

5. **Add GitHub secret**: In the GitHub repo → Settings → Secrets and variables → Actions, add a new secret named `RENDER_DEPLOY_HOOK_URL` with the URL from step 4.

6. **Trigger first deploy**: Push any commit to `main` (or manually trigger from Render dashboard) to start the first deployment.
