# Email Verification — Design Spec

**Date:** 2026-04-23
**Status:** Draft

---

## Context

Lista AI currently registers users and issues tokens without verifying that the submitted email address is controlled by the registrant. This spec adds email verification with these guarantees:

1. **Idempotent** — resend requests, re-clicked links, and worker retries produce consistent results.
2. **Fail-safe** — a down email vendor does not break registration; emails are delivered at-least-once.
3. **Vendor-abstract** — the first vendor is Resend, but the design allows swapping to SES / SendGrid / Mailgun via one adapter class.

The first vendor is **Resend**. A feature flag controls whether verification is enforced, so test/dev environments can keep today's behavior.

---

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **Hard gate on login** when flag enabled | User explicitly chose this over soft-gate or informational-only. |
| 2 | **Feature toggle** `app.email-verification.enabled` | Keeps test/dev environments unaffected; default `false` in tests. |
| 3 | **Universal Link** to `POST /v1/auth/verify-email` | Frontend is React Native; Universal/App Links open the app if installed and fall back to web. |
| 4 | **Transactional outbox + async worker** | Registrations succeed even when Resend is down; retries handle transient failures. |
| 5 | **Resend old token when user re-requests** | Cleanest UX; newer issue wins. |
| 6 | **60s cooldown on resend, 24h TTL on token** | Prevents accidental spam; TTL balances convenience and attack surface. |
| 7 | **Idempotent verify** — same token clicked twice returns 200 both times | No confusing errors for users with flaky mail clients. |

---

## Architecture

```
POST /v1/auth/register
        │
        ▼
AuthService.register(cmd)  ──[TX]─▶  users (verified=false)
                                     verification_tokens (token_hash, user_id, expires_at)
                                     email_outbox (status=PENDING, template=VERIFY_EMAIL)
        │
        └─── 202 Accepted, body: { message: "check your email" }  (no tokens issued)

──────────────────────────────────────────────────────────────────
@Scheduled(fixedDelay=10s)  EmailOutboxWorker
        │
        ▼
outbox.claimPending(50, now)               ← SELECT ... FOR UPDATE SKIP LOCKED
        │
        ▼
emailSender.send(rendered)                 ← EmailSender port (ResendEmailSender)
        │
        ├─ success → row.status=SENT, sent_at=now
        └─ EmailSendException
              ├─ retryable=false OR attempts+1 >= MAX → row.status=FAILED
              └─ else → row.attempts++, last_error, next_attempt_at=now+backoff
──────────────────────────────────────────────────────────────────
Universal Link click  →  React Native app (or web fallback)
        │
        ▼
POST /v1/auth/verify-email { token }
        │
        ▼
AuthService.verifyEmail(token)  ──[TX]─▶  verification_tokens by SHA-256 hash
                                          if null               → 400 INVALID_TOKEN
                                          if revoked_at set     → 410 TOKEN_SUPERSEDED
                                          if expires_at < now   → 410 TOKEN_EXPIRED
                                          if used_at set        → 200 OK (idempotent no-op)
                                          else                  → users.verified=true
                                                                   used_at=now
                                                                   200 OK
──────────────────────────────────────────────────────────────────
POST /v1/auth/login
        │
        ▼
if feature enabled AND user.verified=false → 403 EMAIL_NOT_VERIFIED
else → issue tokens (unchanged)
```

### Three boundaries worth naming

- **`EmailSender` port** — `application/port/output/EmailSender.java`. Only one class in the codebase imports Resend's HTTP contract (`ResendEmailSender`). Swapping vendors is one new adapter, a config flag, zero changes to `AuthService` / worker / tests.
- **Outbox pattern** — user creation and "intent to send" commit in the same DB transaction. The worker is the only thing that talks to Resend; it's restartable, idempotent-at-the-row-level, and invisible when the feature is disabled.
- **Feature toggle** — `@ConditionalOnProperty` on the worker bean and on the gate check in `AuthService`. Off = today's behavior.

---

## Data Model

Three Liquibase migrations.

```yaml
# 008_users_verified_column.yaml
# Add verified flag, backfill existing users to TRUE (grandfathered).
ALTER TABLE users ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET verified = TRUE;  -- predate this feature
```

```sql
-- 009_email_verification_tokens_table.yaml
CREATE TABLE email_verification_tokens (
  id          BIGSERIAL  PRIMARY KEY,
  user_id     BIGINT     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT       NOT NULL UNIQUE,       -- SHA-256 of opaque token
  expires_at  TIMESTAMP  NOT NULL,
  used_at     TIMESTAMP,                        -- idempotency marker for re-clicks
  revoked_at  TIMESTAMP,                        -- set when superseded by a resend
  created_at  TIMESTAMP  NOT NULL DEFAULT now()
);
CREATE INDEX idx_evt_user_id ON email_verification_tokens(user_id);

-- 010_email_outbox_table.yaml
CREATE TABLE email_outbox (
  id              BIGSERIAL  PRIMARY KEY,
  template        TEXT       NOT NULL,          -- "VERIFY_EMAIL" for now
  recipient       TEXT       NOT NULL,
  payload_json    TEXT       NOT NULL,          -- template variables
  status          TEXT       NOT NULL,          -- PENDING | SENT | FAILED
  attempts        INT        NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP  NOT NULL DEFAULT now(),
  last_error      TEXT,
  sent_at         TIMESTAMP,
  created_at      TIMESTAMP  NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_pending ON email_outbox(status, next_attempt_at)
  WHERE status = 'PENDING';
```

**Why `used_at` and `revoked_at` as two nullable timestamps instead of one `status` enum:** the verify endpoint needs to distinguish "already verified" (200 idempotent) from "a newer token was issued for you" (410). A single state field loses the "when" data, which we may want for debugging.

**Why partial index on outbox:** the only hot query is `WHERE status='PENDING' AND next_attempt_at <= now()`; partial index stays tiny as `SENT` rows accumulate.

### Domain model delta

```java
// User gains verified. Breaking constructor change — updated at all call sites.
public record User(Long id, String email, String name, boolean verified) {}

public record EmailVerificationToken(
    Long id, Long userId,
    Instant expiresAt, Instant usedAt, Instant revokedAt
) {}
// email_outbox stays in infrastructure — no domain model, it's an adapter concern.
```

---

## Vendor Abstraction

```
application/port/output/
  EmailSender.java                    ← interface (one method)

application/service/
  EmailOutboxWorker.java              ← @Scheduled, @ConditionalOnProperty
  EmailTemplateRenderer.java          ← renders outbox row → EmailMessage
  exception/EmailNotVerifiedException.java

infrastructure/adapter/output/email/
  ResendEmailSender.java              ← only class that imports Resend's HTTP contract
```

### Port

```java
public interface EmailSender {
    /** @throws EmailSendException on any failure — transient or permanent */
    void send(EmailMessage message);
}

public record EmailMessage(
    String to,
    String subject,
    String htmlBody,
    String textBody                              // plaintext fallback
) {}

public class EmailSendException extends RuntimeException {
    private final boolean retryable;             // worker reads this to decide backoff vs FAILED
}
```

**Shape rationale:**

- One method, one fully-rendered value object. No vendor concepts (from-address, template IDs, tags) leak in. From-address is a constant on the adapter.
- Template rendering happens in the worker *before* calling `send` — the adapter receives finished HTML + text.
- The `retryable` flag on the exception is the only way the adapter signals intent. Resend 5xx / 429 / connection error → `retryable=true`. Resend 400 "invalid email" → `retryable=false`. Without it we'd retry garbage for hours.
- No async in the interface. The worker is already off the request thread; `send` synchronous keeps the adapter ~40 lines.

### Resend adapter

Uses `RestClient` (already in `spring-boot-starter-web`). No Resend SDK.

### Config

```yaml
app:
  email:
    from-address: ${EMAIL_FROM:noreply@lista-ai.com}
    provider: resend                             # hook for @ConditionalOnProperty
    resend:
      api-key: ${RESEND_API_KEY}
      base-url: ${RESEND_BASE_URL:https://api.resend.com}     # overridable for WireMock
    worker:
      poll-interval-ms: 10000
  email-verification:
    enabled: ${EMAIL_VERIFICATION_ENABLED:true}
    token-ttl-hours: 24
    resend-cooldown-seconds: 60
    redirect-base-url: ${VERIFICATION_REDIRECT_BASE_URL:https://app.lista-ai.com/verify-email}
```

### Swap story (concrete)

To move to SES: write `SesEmailSender implements EmailSender`, add `@ConditionalOnProperty(name="app.email.provider", havingValue="ses")` to it and `havingValue="resend"` to `ResendEmailSender`, add an `app.email.ses` config block, flip the env var. Zero changes elsewhere.

---

## Outbox Worker

```java
@Component
@ConditionalOnProperty(name = "app.email-verification.enabled", havingValue = "true")
public class EmailOutboxWorker {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 8;   // ~17h cumulative with backoff below

    @Scheduled(fixedDelayString = "${app.email.worker.poll-interval-ms:10000}")
    public void processOutbox() {
        List<OutboxRow> batch = outboxRepo.claimPending(BATCH_SIZE, clock.instant());
        for (OutboxRow row : batch) {
            try {
                EmailMessage rendered = templateRenderer.render(row);
                emailSender.send(rendered);
                outboxRepo.markSent(row.id(), clock.instant());
            } catch (EmailSendException e) {
                if (!e.isRetryable() || row.attempts() + 1 >= MAX_ATTEMPTS) {
                    outboxRepo.markFailed(row.id(), e.getMessage());
                } else {
                    Instant next = clock.instant().plus(backoff(row.attempts() + 1));
                    outboxRepo.markRetry(row.id(), next, e.getMessage());
                }
            }
        }
    }
    // backoff (exponential, capped): 10s, 30s, 1m, 5m, 15m, 1h, 4h, 12h
}
```

**Details:**

- `claimPending` uses `SELECT ... FOR UPDATE SKIP LOCKED` so horizontal scaling is safe. Free on single instance.
- Template rendering lives in `EmailTemplateRenderer` — one method per template name. For now `renderVerifyEmail(payload) → EmailMessage` returning interpolated HTML + plaintext.
- `MAX_ATTEMPTS=8` with the backoff above exhausts at ~17h — within the 24h token TTL. If Resend stays down longer, row goes `FAILED` but the user can still hit `/resend-verification` to create a fresh row.
- `@ConditionalOnProperty` → when disabled, bean doesn't exist; no polling, no noise.

**Crash safety:** if Resend succeeds but we crash before `markSent`, the row is retried on restart and the user receives a duplicate. We accept at-least-once. Exactly-once would require a Resend dedupe key we don't have; duplicate email beats never-sent email.

---

## Endpoints

| Method | Path | Request | Responses |
|---|---|---|---|
| POST | `/v1/auth/register` | `{email, password, name}` | **202** `{message}` when flag on; **201** `TokenResponse` when flag off (existing status) |
| POST | `/v1/auth/verify-email` | `{token}` | **200** empty · **400** `INVALID_TOKEN` · **410** `TOKEN_EXPIRED` / `TOKEN_SUPERSEDED` |
| POST | `/v1/auth/resend-verification` | `{email}` | **200** empty (always, no enumeration) · **429** `VERIFICATION_COOLDOWN` (only for real unverified users within 60s) |
| POST | `/v1/auth/login` | `{email, password}` | unchanged · **403** `EMAIL_NOT_VERIFIED` added when flag on |

### Verify endpoint logic

```
POST /v1/auth/verify-email { token }
  hash = SHA-256(token)
  row = verification_tokens.findByHash(hash)
  if row == null              → 400 INVALID_TOKEN
  if row.revoked_at != null   → 410 TOKEN_SUPERSEDED
  if row.expires_at < now     → 410 TOKEN_EXPIRED
  if row.used_at != null      → 200 OK  (idempotent no-op)
  else                         → [TX] users.verified=true, row.used_at=now → 200 OK
```

Response body is identical for "just verified" and "already verified" — callers cannot distinguish, which is the point.

### Resend endpoint logic

```
POST /v1/auth/resend-verification { email }
  user = users.findByEmail(email)
  if user == null OR user.verified → 200 OK  (no enumeration)

  latest = verification_tokens.findLatestByUserId(user.id)
  if latest != null AND (now - latest.created_at) < 60s → 429 VERIFICATION_COOLDOWN

  [TX]
    if latest != null AND latest.used_at == null → latest.revoked_at = now
    insert new verification_tokens row (24h TTL)
    insert new email_outbox row (PENDING)
  200 OK
```

Cooldown is per-user via the latest token's `created_at` — no separate rate-limit table.

---

## Security

- **Raw tokens never logged.** Only SHA-256 hash prefix (8 chars) for correlation if needed.
- **No user enumeration** on `/resend-verification` — 200 for unknown emails, 200 for already-verified, 429 only for real unverified users within cooldown.
- **Token as SHA-256 hash in DB**, mirroring the existing `refresh_tokens` pattern.
- **24h TTL** — long enough for cross-device email checking, short enough that stale DB snapshots lose value quickly.
- **Universal Link base URL** is trusted config, not user input. No open-redirect risk because the server constructs the full URL and the token is server-generated.
- **Google OAuth users inherit `email_verified`** from the ID token claims. If `email_verified=false` (rare, custom Workspace domains), they hit the same gate as local registrations.
- **Backfill guarantee** — migration 008 sets `verified=TRUE` for all existing users before the flag is ever enabled. No production lockout.
- **New secret:** `RESEND_API_KEY` added to Render dashboard env vars.

---

## Testing Strategy

Reuses `BaseIntegrationTest` and WireMock (already used for Google JWKS).

**Default for tests:** `app.email-verification.enabled=false` in `src/test/resources/application.yaml`. Existing tests (`AuthControllerIT`, `ListControllerIT`, etc.) keep working unchanged. A small set of new tests opt in via `@TestPropertySource`.

### Unit tests

- **`AuthServiceTest`** — extended for:
  - `register` with flag off → issues tokens, no outbox row (existing behavior preserved)
  - `register` with flag on → no tokens, `verified=false`, 1 token + 1 outbox row
  - `verifyEmail` happy / expired / revoked / already-used idempotent / unknown
  - `resendVerification` happy / unknown email (200) / already verified (200) / cooldown (429) / old token revoked
  - `loginLocal` with `verified=false` + flag on → `EmailNotVerifiedException`
  - `loginLocal` with `verified=false` + flag off → succeeds (grandfathering)
  - `loginGoogle` new user → `verified` set from Google `email_verified` claim

- **`EmailOutboxWorkerTest`** — mocks `EmailSender`:
  - Success → `SENT`
  - `retryable=true` → attempts++, next_attempt_at with backoff
  - `retryable=false` → immediate `FAILED`
  - At max attempts → `FAILED`
  - Mixed batch — rows handled independently

- **`EmailTemplateRendererTest`** — snapshot-style: rendered HTML contains the verify URL with the token; plaintext fallback matches expected shape.

- **`ResendEmailSenderTest`** — WireMock stubs `POST /emails`:
  - 200 → no exception
  - 401/400 → `EmailSendException(retryable=false)`
  - 429 / 5xx / connection error → `EmailSendException(retryable=true)`

### Integration tests

- **`EmailVerificationIT`** — extends `BaseIntegrationTest`, flag enabled, Resend base URL pointed at WireMock:
  - Full flow: register (202, no tokens) → outbox row exists → trigger worker → WireMock verifies call → verify-email 200 → login now returns tokens
  - Login before verify → 403 `EMAIL_NOT_VERIFIED`
  - Verify twice with same token → both 200, no state change on the second
  - Resend within 60s → 429; after cooldown → 200 and old token `revoked_at` set
  - Resend API returns 500 → outbox `PENDING` with `attempts=1` and `next_attempt_at` in the future; advance clock, second worker run marks `SENT`

- **`EmailVerificationDisabledIT`** — flag explicitly off, register still returns tokens.

### Test plumbing

- **Worker triggered directly in tests** — inject the bean and call `processOutbox()` instead of waiting on `@Scheduled`.
- **`Clock` bean** — introduced and injected into `AuthService` and the worker so time-sensitive tests use a fixed clock. Production config wires `Clock.systemUTC()`.

### Out of scope for testing

- No load tests, no chaos tests.
- No tests against real Resend — WireMock is the contract test.
- No DB partial-index test — infra concern, not behavior.

---

## Files Changed

### New

```
db/changelog/migration/008_users_verified_column.yaml
db/changelog/migration/009_email_verification_tokens_table.yaml
db/changelog/migration/010_email_outbox_table.yaml

domain/model/EmailVerificationToken.java
application/port/output/EmailSender.java
application/port/output/EmailVerificationTokenRepository.java
application/port/output/EmailOutboxRepository.java
application/port/input/command/VerifyEmailCommand.java
application/port/input/command/ResendVerificationCommand.java
application/service/EmailOutboxWorker.java
application/service/EmailTemplateRenderer.java
application/service/exception/EmailNotVerifiedException.java

infrastructure/adapter/output/email/ResendEmailSender.java
infrastructure/adapter/output/email/dto/ResendSendRequest.java
infrastructure/adapter/output/email/dto/ResendSendResponse.java
infrastructure/adapter/output/persistence/EmailVerificationTokenPersistenceAdapter.java
infrastructure/adapter/output/persistence/EmailOutboxPersistenceAdapter.java
infrastructure/adapter/output/persistence/entity/EmailVerificationTokenEntity.java
infrastructure/adapter/output/persistence/entity/EmailOutboxEntity.java
infrastructure/adapter/output/persistence/mapper/EmailVerificationTokenPersistenceMapper.java
infrastructure/adapter/output/persistence/repository/EmailVerificationTokenJpaRepository.java
infrastructure/adapter/output/persistence/repository/EmailOutboxJpaRepository.java
infrastructure/adapter/input/rest/dto/VerifyEmailRequest.java
infrastructure/adapter/input/rest/dto/ResendVerificationRequest.java
infrastructure/config/EmailVerificationProperties.java
infrastructure/config/EmailProperties.java
infrastructure/config/SchedulingConfig.java                  (@EnableScheduling)
infrastructure/config/ClockConfig.java                       (@Bean Clock systemUTC)
```

### Modified

```
domain/model/User.java                                       (+ verified field)
application/service/AuthService.java                         (register, login, verifyEmail, resendVerification)
application/service/GoogleAuthProvider.java                  (pass email_verified through)
application/port/input/AuthUseCase.java                      (+ verifyEmail, resendVerification)
application/port/input/AuthIdentity.java                     (+ emailVerified)
application/port/output/UserRepository.java                  (+ setVerified)
infrastructure/adapter/input/rest/AuthController.java        (+ 2 endpoints, register response change)
infrastructure/adapter/input/rest/mapper/AuthRestMapper.java
src/main/resources/application.yaml                          (+ email, email-verification, worker blocks)
```

### Not touched

- `build.gradle.kts` — no new dependencies (RestClient + WireMock already present).
- `db.changelog-master.yaml` — uses `includeAll`, picks up new migration files automatically.
- `render.yaml` — env vars are set in Render dashboard, not in code.

---

## Out of Scope

- **Password reset** — will reuse outbox + `EmailSender` + template renderer. Separate spec.
- **Email change flow.**
- **Monitoring / alerting** on `email_outbox.status='FAILED'` rows — we log WARN; dashboards are separate.
- **Multi-template UI** in Thymeleaf or similar — single interpolated string per template for now.
- **Rate limiting on `/register`** — orthogonal to this feature.
