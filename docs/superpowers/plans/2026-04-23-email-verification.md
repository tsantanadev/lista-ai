# Email Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add email verification to Lista AI using Resend as the first vendor, with a feature-toggled hard gate on login, transactional outbox for fail-safe delivery, and an `EmailSender` port for vendor abstraction.

**Architecture:** When the `app.email-verification.enabled` flag is on, `POST /v1/auth/register` creates an unverified user and inserts an `email_outbox` row (status `PENDING`) in the same transaction. A `@Scheduled` worker claims pending rows, calls the `EmailSender` port (implemented by `ResendEmailSender`), and marks them `SENT` or retries with exponential backoff. The verification link is a Universal Link to a React Native app that calls `POST /v1/auth/verify-email { token }`. Login rejects unverified users with 403. When the flag is off, registration behaves exactly as today.

**Tech Stack:** Java 25 · Spring Boot 4.0 · Liquibase · JPA/Hibernate · MapStruct · PostgreSQL · Resend HTTP API · WireMock · Testcontainers · RestAssured · JUnit 5

**Related Docs:** [Design Spec](../specs/2026-04-23-email-verification-design.md)

---

## File Structure

**Created:**

```
src/main/resources/db/changelog/migration/008_users_verified_column.yaml
src/main/resources/db/changelog/migration/009_email_verification_tokens_table.yaml
src/main/resources/db/changelog/migration/010_email_outbox_table.yaml

src/main/java/com/listaai/domain/model/EmailVerificationToken.java

src/main/java/com/listaai/application/port/output/EmailSender.java
src/main/java/com/listaai/application/port/output/EmailMessage.java
src/main/java/com/listaai/application/port/output/EmailVerificationTokenRepository.java
src/main/java/com/listaai/application/port/output/EmailOutboxRepository.java
src/main/java/com/listaai/application/port/input/command/VerifyEmailCommand.java
src/main/java/com/listaai/application/port/input/command/ResendVerificationCommand.java

src/main/java/com/listaai/application/service/EmailOutboxWorker.java
src/main/java/com/listaai/application/service/EmailTemplateRenderer.java
src/main/java/com/listaai/application/service/exception/EmailSendException.java
src/main/java/com/listaai/application/service/exception/EmailNotVerifiedException.java

src/main/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSender.java

src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailVerificationTokenPersistenceAdapter.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailOutboxPersistenceAdapter.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailVerificationTokenEntity.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailOutboxEntity.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/mapper/EmailVerificationTokenPersistenceMapper.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailVerificationTokenJpaRepository.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailOutboxJpaRepository.java

src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/VerifyEmailRequest.java
src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ResendVerificationRequest.java
src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/RegisterPendingResponse.java

src/main/java/com/listaai/infrastructure/config/ClockConfig.java
src/main/java/com/listaai/infrastructure/config/SchedulingConfig.java
src/main/java/com/listaai/infrastructure/config/EmailProperties.java
src/main/java/com/listaai/infrastructure/config/EmailVerificationProperties.java

src/test/java/com/listaai/application/service/EmailOutboxWorkerTest.java
src/test/java/com/listaai/application/service/EmailTemplateRendererTest.java
src/test/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSenderTest.java
src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationIT.java
src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationDisabledIT.java
```

**Modified:**

```
src/main/java/com/listaai/domain/model/User.java                                  (+ verified)
src/main/java/com/listaai/application/port/input/AuthUseCase.java                 (+ verifyEmail, resendVerification)
src/main/java/com/listaai/application/port/input/AuthIdentity.java                (+ emailVerified)
src/main/java/com/listaai/application/port/output/UserRepository.java             (save signature gains verified)
src/main/java/com/listaai/application/service/AuthService.java                    (flag-aware flow)
src/main/java/com/listaai/application/service/GoogleAuthProvider.java             (read email_verified claim)
src/main/java/com/listaai/infrastructure/adapter/output/persistence/UserPersistenceAdapter.java
src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/UserEntity.java
src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java   (+ 2 endpoints, dynamic register status)
src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java  (+ EmailNotVerifiedException, + 410 / 429)
src/main/resources/application.yaml                                               (+ email / email-verification blocks)
src/test/resources/application.yaml                                               (+ flag=false, wiremock base-urls)
src/test/java/com/listaai/BaseIntegrationTest.java                                (TRUNCATE new tables)
src/test/java/com/listaai/application/service/AuthServiceTest.java                (User.verified updates, Clock)
```

**Conventions to follow (from existing code):**
- Liquibase YAML migration format with `changeSet` id matching filename prefix (see `005_refresh_tokens_table.yaml`).
- Domain records are immutable, no external deps (see `User.java`, `OAuthIdentity.java`).
- Persistence adapters implement port interfaces, MapStruct mappers convert entity ↔ domain (see `UserPersistenceAdapter`, `UserPersistenceMapper`).
- REST DTOs are records, REST mappers use MapStruct (see `AuthRestMapper`).
- Integration tests extend `BaseIntegrationTest`, use RestAssured.
- WireMock runs on a fixed port (9090 for Google JWKS). Use a different fixed port for Resend (e.g. 9091).
- JaCoCo coverage minimum 90% (configured in `build.gradle.kts`); services and adapters count, configs/entities/ports/DTOs are excluded.

---

## Task 1: Disable feature flag in test resources (safety baseline)

**Files:**
- Modify: `src/test/resources/application.yaml`

Before any code changes, add the flag to test config so when we later consume it, every existing test keeps running as today.

- [ ] **Step 1: Add the flag (and the email config block with stub URLs) to test resources**

Edit `src/test/resources/application.yaml`:

```yaml
app:
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHktMzItY2hhcnMhISE=
    expiration-seconds: 900
    refresh-expiration-days: 7
  auth:
    google:
      jwks-uri: http://localhost:9090/oauth2/v3/certs
      client-id: test-google-client-id
  email:
    from-address: noreply@test.local
    provider: resend
    resend:
      api-key: test-resend-key
      base-url: http://localhost:9091
    worker:
      poll-interval-ms: 10000
  email-verification:
    enabled: false
    token-ttl-hours: 24
    resend-cooldown-seconds: 60
    redirect-base-url: https://app.test.local/verify-email
```

- [ ] **Step 2: Run all tests to confirm no regressions**

Run: `./gradlew test`
Expected: PASS (all existing tests green; we haven't read these properties yet).

- [ ] **Step 3: Commit**

```bash
git add src/test/resources/application.yaml
git commit -m "test: add email verification config defaults (flag off)"
```

---

## Task 2: Add `verified` column to users + domain field

**Files:**
- Create: `src/main/resources/db/changelog/migration/008_users_verified_column.yaml`
- Modify: `src/main/java/com/listaai/domain/model/User.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/UserEntity.java`
- Modify: `src/main/java/com/listaai/application/port/output/UserRepository.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/UserPersistenceAdapter.java`
- Modify: `src/main/java/com/listaai/application/service/AuthService.java` (User constructor calls)
- Modify: `src/test/java/com/listaai/application/service/AuthServiceTest.java` (User constructors, `save` mock signatures)
- Test: reuse existing `AuthServiceTest` + `AuthControllerIT`

- [ ] **Step 1: Write failing test — existing user stays verified, new user defaults to verified when flag off**

Add to `src/test/java/com/listaai/application/service/AuthServiceTest.java` (or adapt the existing `register_persistsUser_andIssuesTokens` test):

```java
@Test
void register_whenFlagOff_savesUserAsVerified() {
    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("pwd")).thenReturn("HASHED");
    when(userRepository.save(any(User.class), eq("HASHED")))
        .thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(42L, u.email(), u.name(), u.verified());
        });

    authService.register(new RegisterCommand("new@example.com", "pwd", "New User"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture(), eq("HASHED"));
    assertThat(captor.getValue().verified()).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest.register_whenFlagOff_savesUserAsVerified"`
Expected: FAIL — `User` does not have a `verified` accessor.

- [ ] **Step 3: Update `User` domain record**

Edit `src/main/java/com/listaai/domain/model/User.java`:

```java
package com.listaai.domain.model;

public record User(Long id, String email, String name, boolean verified) {}
```

- [ ] **Step 4: Create migration 008**

Create `src/main/resources/db/changelog/migration/008_users_verified_column.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 008
      author: lista-ai
      changes:
        - addColumn:
            tableName: users
            columns:
              - column:
                  name: verified
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
        - update:
            tableName: users
            columns:
              - column:
                  name: verified
                  valueBoolean: true
```

- [ ] **Step 5: Add `verified` to `UserEntity`**

Edit `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/UserEntity.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {}

    public UserEntity(String email, String name, String passwordHash, boolean verified) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.verified = verified;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 6: Update `UserRepository` port — add `setVerified` for later verify flow**

Edit `src/main/java/com/listaai/application/port/output/UserRepository.java`:

```java
package com.listaai.application.port.output;

import com.listaai.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    Optional<UserWithHash> findByEmailWithHash(String email);
    User save(User user, String passwordHash);
    void setVerified(Long userId, boolean verified);

    record UserWithHash(User user, String passwordHash) {}
}
```

- [ ] **Step 7: Update `UserPersistenceAdapter` to persist + expose `verified`**

Replace the body of `src/main/java/com/listaai/infrastructure/adapter/output/persistence/UserPersistenceAdapter.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.UserPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<UserRepository.UserWithHash> findByEmailWithHash(String email) {
        return jpaRepository.findByEmail(email)
                .map(e -> new UserRepository.UserWithHash(mapper.toDomain(e), e.getPasswordHash()));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public User save(User user, String passwordHash) {
        UserEntity entity = new UserEntity(user.email(), user.name(), passwordHash, user.verified());
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void setVerified(Long userId, boolean verified) {
        jpaRepository.findById(userId).ifPresent(e -> e.setVerified(verified));
    }
}
```

(`UserPersistenceMapper` is a MapStruct interface; the auto-generated mapping already picks up the new `verified` field without edits because names match.)

- [ ] **Step 8: Update `AuthService` to pass `verified=true` when creating users (both register and Google new-user path)**

In `src/main/java/com/listaai/application/service/AuthService.java`, replace both `new User(...)` calls:

- Line in `register`:
  ```java
  User user = userRepository.save(
          new User(null, command.email(), command.name(), true), hash);
  ```
- Line in `loginGoogle` for the new-user path:
  ```java
  user = userRepository.findByEmail(identity.email())
          .orElseGet(() -> userRepository.save(
                  new User(null, identity.email(), identity.name(), true), null));
  ```

(Why `true` here: with the flag off — today's default — users should be logged in immediately. Task 12 will introduce the flag-aware path that switches to `false` when enabled.)

- [ ] **Step 9: Update `AuthServiceTest` — every stubbed `userRepository.save` answer and test assertion that constructs a `User` must include the new `verified` arg**

Every `new User(...)` in tests gets a 4-arg form. Find/replace approach: search for `new User(` in `AuthServiceTest.java` and add `, true` before the closing `)`. The existing mocks using `any(User.class)` keep working.

- [ ] **Step 10: Run all tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/db/changelog/migration/008_users_verified_column.yaml \
        src/main/java/com/listaai/domain/model/User.java \
        src/main/java/com/listaai/application/port/output/UserRepository.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/UserEntity.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/UserPersistenceAdapter.java \
        src/main/java/com/listaai/application/service/AuthService.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: add verified flag to User, backfill existing users"
```

---

## Task 3: Introduce `Clock` bean and inject into `AuthService`

Sets up deterministic time for later time-sensitive features (token TTL, resend cooldown, outbox backoff).

**Files:**
- Create: `src/main/java/com/listaai/infrastructure/config/ClockConfig.java`
- Modify: `src/main/java/com/listaai/application/service/AuthService.java`
- Modify: `src/test/java/com/listaai/application/service/AuthServiceTest.java`

- [ ] **Step 1: Write a failing test asserting `AuthService` uses the injected clock**

In `AuthServiceTest.java`, add:

```java
@Test
void issueTokens_usesInjectedClockForExpiry() {
    Clock fixed = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
            refreshTokenRepository, authProviderRegistry, jwtTokenService,
            passwordEncoder, 7L, fixed);

    when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("H");
    when(userRepository.save(any(User.class), any()))
        .thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(1L, u.email(), u.name(), u.verified());
        });
    when(jwtTokenService.generateRefreshToken()).thenReturn("RAW");
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");

    svc.register(new RegisterCommand("x@x.com", "pwd", "X"));

    verify(refreshTokenRepository).save(eq(1L), eq("HASH"),
        eq(Instant.parse("2026-04-30T10:00:00Z")));
}
```

(All other `AuthService` constructor calls in the test file also need the `Clock` arg — use `Clock.systemUTC()` in `@BeforeEach` setup.)

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: FAIL — `AuthService` constructor doesn't accept `Clock`.

- [ ] **Step 3: Create `ClockConfig`**

Create `src/main/java/com/listaai/infrastructure/config/ClockConfig.java`:

```java
package com.listaai.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 4: Inject `Clock` into `AuthService`**

Modify `AuthService` to accept `Clock` and replace `Instant.now()` with `clock.instant()`. Replace constructor + `issueTokens`:

```java
// field
private final Clock clock;

// constructor — add last parameter:
public AuthService(
        UserRepository userRepository,
        OAuthIdentityRepository oAuthIdentityRepository,
        RefreshTokenRepository refreshTokenRepository,
        AuthProviderRegistry authProviderRegistry,
        JwtTokenService jwtTokenService,
        PasswordEncoder passwordEncoder,
        @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays,
        Clock clock) {
    // ... existing assignments ...
    this.clock = clock;
}

// issueTokens — replace Instant.now():
private AuthResult issueTokens(User user) {
    String accessToken = jwtTokenService.generateAccessToken(user);
    String refreshToken = jwtTokenService.generateRefreshToken();
    String refreshTokenHash = jwtTokenService.hashRefreshToken(refreshToken);
    Instant expiresAt = clock.instant().plus(refreshExpirationDays, ChronoUnit.DAYS);
    refreshTokenRepository.save(user.id(), refreshTokenHash, expiresAt);
    return new AuthResult(accessToken, refreshToken, ACCESS_TOKEN_EXPIRY_SECONDS);
}
```

- [ ] **Step 5: Update existing `@BeforeEach` in `AuthServiceTest` to pass `Clock.systemUTC()`**

Replace the constructor invocation in the test's setUp so existing tests compile:

```java
authService = new AuthService(userRepository, oAuthIdentityRepository,
        refreshTokenRepository, authProviderRegistry, jwtTokenService,
        passwordEncoder, 7L, Clock.systemUTC());
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/config/ClockConfig.java \
        src/main/java/com/listaai/application/service/AuthService.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: inject Clock into AuthService for deterministic time"
```

---

## Task 4: Config property classes + `@EnableScheduling`

**Files:**
- Create: `src/main/java/com/listaai/infrastructure/config/EmailProperties.java`
- Create: `src/main/java/com/listaai/infrastructure/config/EmailVerificationProperties.java`
- Create: `src/main/java/com/listaai/infrastructure/config/SchedulingConfig.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Create `EmailProperties`**

Create `src/main/java/com/listaai/infrastructure/config/EmailProperties.java`:

```java
package com.listaai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String fromAddress,
        String provider,
        Resend resend,
        Worker worker
) {
    public record Resend(String apiKey, String baseUrl) {}
    public record Worker(long pollIntervalMs) {}
}
```

- [ ] **Step 2: Create `EmailVerificationProperties`**

Create `src/main/java/com/listaai/infrastructure/config/EmailVerificationProperties.java`:

```java
package com.listaai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
        boolean enabled,
        int tokenTtlHours,
        int resendCooldownSeconds,
        String redirectBaseUrl
) {}
```

- [ ] **Step 3: Enable scheduling + configuration properties**

Create `src/main/java/com/listaai/infrastructure/config/SchedulingConfig.java`:

```java
package com.listaai.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({EmailProperties.class, EmailVerificationProperties.class})
public class SchedulingConfig {
}
```

- [ ] **Step 4: Add email config blocks to main `application.yaml`**

In `src/main/resources/application.yaml`, add under `app:`:

```yaml
  email:
    from-address: ${EMAIL_FROM:noreply@lista-ai.com}
    provider: resend
    resend:
      api-key: ${RESEND_API_KEY:}
      base-url: ${RESEND_BASE_URL:https://api.resend.com}
    worker:
      poll-interval-ms: 10000
  email-verification:
    enabled: ${EMAIL_VERIFICATION_ENABLED:true}
    token-ttl-hours: 24
    resend-cooldown-seconds: 60
    redirect-base-url: ${VERIFICATION_REDIRECT_BASE_URL:https://app.lista-ai.com/verify-email}
```

- [ ] **Step 5: Run tests to confirm config binding works**

Run: `./gradlew test`
Expected: PASS (no behavior uses these properties yet, but the context must load).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/config/EmailProperties.java \
        src/main/java/com/listaai/infrastructure/config/EmailVerificationProperties.java \
        src/main/java/com/listaai/infrastructure/config/SchedulingConfig.java \
        src/main/resources/application.yaml
git commit -m "feat: add email + email-verification configuration properties"
```

---

## Task 5: `EmailSender` port + `EmailMessage` + exceptions

**Files:**
- Create: `src/main/java/com/listaai/application/port/output/EmailSender.java`
- Create: `src/main/java/com/listaai/application/port/output/EmailMessage.java`
- Create: `src/main/java/com/listaai/application/service/exception/EmailSendException.java`
- Create: `src/main/java/com/listaai/application/service/exception/EmailNotVerifiedException.java`

No behavior tests yet — these are definitions consumed in later tasks.

- [ ] **Step 1: Create `EmailMessage`**

Create `src/main/java/com/listaai/application/port/output/EmailMessage.java`:

```java
package com.listaai.application.port.output;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {}
```

- [ ] **Step 2: Create `EmailSender` port**

Create `src/main/java/com/listaai/application/port/output/EmailSender.java`:

```java
package com.listaai.application.port.output;

import com.listaai.application.service.exception.EmailSendException;

public interface EmailSender {
    /**
     * Sends a fully-rendered message. Vendor concepts (API keys, template IDs)
     * must not appear in this interface.
     *
     * @throws EmailSendException on any failure. The exception's {@code retryable}
     *         flag tells callers whether to schedule a retry.
     */
    void send(EmailMessage message);
}
```

- [ ] **Step 3: Create `EmailSendException`**

Create `src/main/java/com/listaai/application/service/exception/EmailSendException.java`:

```java
package com.listaai.application.service.exception;

public class EmailSendException extends RuntimeException {
    private final boolean retryable;

    public EmailSendException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmailSendException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
```

- [ ] **Step 4: Create `EmailNotVerifiedException`**

Create `src/main/java/com/listaai/application/service/exception/EmailNotVerifiedException.java`:

```java
package com.listaai.application.service.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email address not verified");
    }
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/listaai/application/port/output/EmailSender.java \
        src/main/java/com/listaai/application/port/output/EmailMessage.java \
        src/main/java/com/listaai/application/service/exception/EmailSendException.java \
        src/main/java/com/listaai/application/service/exception/EmailNotVerifiedException.java
git commit -m "feat: add EmailSender port and email-related exceptions"
```

---

## Task 6: Verification token — migration, domain, persistence

**Files:**
- Create: `src/main/resources/db/changelog/migration/009_email_verification_tokens_table.yaml`
- Create: `src/main/java/com/listaai/domain/model/EmailVerificationToken.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailVerificationTokenEntity.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/mapper/EmailVerificationTokenPersistenceMapper.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailVerificationTokenJpaRepository.java`
- Create: `src/main/java/com/listaai/application/port/output/EmailVerificationTokenRepository.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailVerificationTokenPersistenceAdapter.java`
- Modify: `src/test/java/com/listaai/BaseIntegrationTest.java` (add new table to TRUNCATE list)

Persistence is covered by later integration tests (following the project convention — adapters are tested through the stack, not individually).

- [ ] **Step 1: Create migration 009**

Create `src/main/resources/db/changelog/migration/009_email_verification_tokens_table.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 009
      author: lista-ai
      changes:
        - createTable:
            tableName: email_verification_tokens
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
                    foreignKeyName: fk_evt_user
                    references: users(id)
                    deleteCascade: true
              - column:
                  name: token_hash
                  type: TEXT
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: expires_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: used_at
                  type: TIMESTAMP
              - column:
                  name: revoked_at
                  type: TIMESTAMP
              - column:
                  name: created_at
                  type: TIMESTAMP
                  defaultValueDate: now()
                  constraints:
                    nullable: false
        - createIndex:
            indexName: idx_evt_user_id
            tableName: email_verification_tokens
            columns:
              - column:
                  name: user_id
```

- [ ] **Step 2: Create domain record**

Create `src/main/java/com/listaai/domain/model/EmailVerificationToken.java`:

```java
package com.listaai.domain.model;

import java.time.Instant;

public record EmailVerificationToken(
        Long id,
        Long userId,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        Instant createdAt
) {
    public boolean isUsed() { return usedAt != null; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
}
```

- [ ] **Step 3: Create JPA entity**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailVerificationTokenEntity.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerificationTokenEntity() {}

    public EmailVerificationTokenEntity(
            Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Create MapStruct mapper**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/mapper/EmailVerificationTokenPersistenceMapper.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmailVerificationTokenPersistenceMapper {
    EmailVerificationToken toDomain(EmailVerificationTokenEntity entity);
}
```

- [ ] **Step 5: Create Spring Data JPA repository**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailVerificationTokenJpaRepository.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailVerificationTokenJpaRepository
        extends JpaRepository<EmailVerificationTokenEntity, Long> {
    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);
    Optional<EmailVerificationTokenEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 6: Create output port**

Create `src/main/java/com/listaai/application/port/output/EmailVerificationTokenRepository.java`:

```java
package com.listaai.application.port.output;

import com.listaai.domain.model.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(Long userId, String tokenHash, Instant expiresAt, Instant createdAt);
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    Optional<EmailVerificationToken> findLatestByUserId(Long userId);
    void markUsed(Long id, Instant usedAt);
    void markRevoked(Long id, Instant revokedAt);
}
```

- [ ] **Step 7: Create persistence adapter**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailVerificationTokenPersistenceAdapter.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.EmailVerificationTokenRepository;
import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.EmailVerificationTokenPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.EmailVerificationTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Component
public class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenRepository {

    private final EmailVerificationTokenJpaRepository jpa;
    private final EmailVerificationTokenPersistenceMapper mapper;

    public EmailVerificationTokenPersistenceAdapter(
            EmailVerificationTokenJpaRepository jpa,
            EmailVerificationTokenPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public EmailVerificationToken save(Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        EmailVerificationTokenEntity e = new EmailVerificationTokenEntity(userId, tokenHash, expiresAt, createdAt);
        return mapper.toDomain(jpa.save(e));
    }

    @Override
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public Optional<EmailVerificationToken> findLatestByUserId(Long userId) {
        return jpa.findFirstByUserIdOrderByCreatedAtDesc(userId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markUsed(Long id, Instant usedAt) {
        jpa.findById(id).ifPresent(e -> e.setUsedAt(usedAt));
    }

    @Override
    @Transactional
    public void markRevoked(Long id, Instant revokedAt) {
        jpa.findById(id).ifPresent(e -> e.setRevokedAt(revokedAt));
    }
}
```

- [ ] **Step 8: Add new table to `BaseIntegrationTest` TRUNCATE**

Edit `src/test/java/com/listaai/BaseIntegrationTest.java` — change the TRUNCATE line to include `email_verification_tokens`:

```java
jdbcTemplate.execute(
    "TRUNCATE TABLE item_list, user_shopping_list, list, oauth_identities, refresh_tokens, email_verification_tokens, users RESTART IDENTITY CASCADE");
```

- [ ] **Step 9: Run all tests**

Run: `./gradlew test`
Expected: PASS. Liquibase applies the new migration at startup; Hibernate `validate` must be happy.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/changelog/migration/009_email_verification_tokens_table.yaml \
        src/main/java/com/listaai/domain/model/EmailVerificationToken.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailVerificationTokenEntity.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/mapper/EmailVerificationTokenPersistenceMapper.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailVerificationTokenJpaRepository.java \
        src/main/java/com/listaai/application/port/output/EmailVerificationTokenRepository.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailVerificationTokenPersistenceAdapter.java \
        src/test/java/com/listaai/BaseIntegrationTest.java
git commit -m "feat: add email_verification_tokens table, domain, persistence"
```

---

## Task 7: Email outbox — migration, entity, repository

**Files:**
- Create: `src/main/resources/db/changelog/migration/010_email_outbox_table.yaml`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailOutboxEntity.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailOutboxJpaRepository.java`
- Create: `src/main/java/com/listaai/application/port/output/EmailOutboxRepository.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailOutboxPersistenceAdapter.java`
- Modify: `src/test/java/com/listaai/BaseIntegrationTest.java` (TRUNCATE)

The outbox entity is exposed as a record from the port — no separate domain model (it's an adapter concern, per spec).

- [ ] **Step 1: Create migration 010**

Create `src/main/resources/db/changelog/migration/010_email_outbox_table.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 010
      author: lista-ai
      changes:
        - createTable:
            tableName: email_outbox
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: template
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: recipient
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: payload_json
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: attempts
                  type: INT
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: next_attempt_at
                  type: TIMESTAMP
                  defaultValueDate: now()
                  constraints:
                    nullable: false
              - column:
                  name: last_error
                  type: TEXT
              - column:
                  name: sent_at
                  type: TIMESTAMP
              - column:
                  name: created_at
                  type: TIMESTAMP
                  defaultValueDate: now()
                  constraints:
                    nullable: false
        - sql:
            sql: CREATE INDEX idx_outbox_pending ON email_outbox (status, next_attempt_at) WHERE status = 'PENDING'
```

(Partial indexes aren't natively in Liquibase YAML; using raw SQL.)

- [ ] **Step 2: Create entity**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailOutboxEntity.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailOutboxEntity() {}

    public EmailOutboxEntity(String template, String recipient, String payloadJson,
                             String status, Instant nextAttemptAt, Instant createdAt) {
        this.template = template;
        this.recipient = recipient;
        this.payloadJson = payloadJson;
        this.status = status;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTemplate() { return template; }
    public String getRecipient() { return recipient; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: Create JPA repository with `claimPending` using native query + SKIP LOCKED**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailOutboxJpaRepository.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.EmailOutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailOutboxJpaRepository extends JpaRepository<EmailOutboxEntity, Long> {

    @Query(value = """
           SELECT * FROM email_outbox
           WHERE status = 'PENDING' AND next_attempt_at <= :now
           ORDER BY next_attempt_at
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<EmailOutboxEntity> claimPending(@Param("now") Instant now, @Param("limit") int limit);
}
```

- [ ] **Step 4: Create output port**

Create `src/main/java/com/listaai/application/port/output/EmailOutboxRepository.java`:

```java
package com.listaai.application.port.output;

import java.time.Instant;
import java.util.List;

public interface EmailOutboxRepository {

    Long enqueue(String template, String recipient, String payloadJson, Instant now);

    /** Locks and returns rows with status=PENDING and next_attempt_at <= now. */
    List<OutboxRow> claimPending(Instant now, int limit);

    void markSent(Long id, Instant sentAt);
    void markRetry(Long id, int attempts, Instant nextAttemptAt, String lastError);
    void markFailed(Long id, int attempts, String lastError);

    record OutboxRow(
            Long id,
            String template,
            String recipient,
            String payloadJson,
            int attempts
    ) {}
}
```

- [ ] **Step 5: Create persistence adapter**

Create `src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailOutboxPersistenceAdapter.java`:

```java
package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailOutboxEntity;
import com.listaai.infrastructure.adapter.output.persistence.repository.EmailOutboxJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class EmailOutboxPersistenceAdapter implements EmailOutboxRepository {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final EmailOutboxJpaRepository jpa;

    public EmailOutboxPersistenceAdapter(EmailOutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Long enqueue(String template, String recipient, String payloadJson, Instant now) {
        EmailOutboxEntity e = new EmailOutboxEntity(template, recipient, payloadJson, STATUS_PENDING, now, now);
        return jpa.save(e).getId();
    }

    @Override
    @Transactional
    public List<OutboxRow> claimPending(Instant now, int limit) {
        return jpa.claimPending(now, limit).stream()
                .map(e -> new OutboxRow(e.getId(), e.getTemplate(), e.getRecipient(), e.getPayloadJson(), e.getAttempts()))
                .toList();
    }

    @Override
    @Transactional
    public void markSent(Long id, Instant sentAt) {
        jpa.findById(id).ifPresent(e -> {
            e.setStatus(STATUS_SENT);
            e.setSentAt(sentAt);
        });
    }

    @Override
    @Transactional
    public void markRetry(Long id, int attempts, Instant nextAttemptAt, String lastError) {
        jpa.findById(id).ifPresent(e -> {
            e.setAttempts(attempts);
            e.setNextAttemptAt(nextAttemptAt);
            e.setLastError(lastError);
        });
    }

    @Override
    @Transactional
    public void markFailed(Long id, int attempts, String lastError) {
        jpa.findById(id).ifPresent(e -> {
            e.setStatus(STATUS_FAILED);
            e.setAttempts(attempts);
            e.setLastError(lastError);
        });
    }
}
```

- [ ] **Step 6: Add `email_outbox` to `BaseIntegrationTest` TRUNCATE**

Edit `src/test/java/com/listaai/BaseIntegrationTest.java`:

```java
jdbcTemplate.execute(
    "TRUNCATE TABLE item_list, user_shopping_list, list, oauth_identities, refresh_tokens, email_verification_tokens, email_outbox, users RESTART IDENTITY CASCADE");
```

- [ ] **Step 7: Run all tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/changelog/migration/010_email_outbox_table.yaml \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/entity/EmailOutboxEntity.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/repository/EmailOutboxJpaRepository.java \
        src/main/java/com/listaai/application/port/output/EmailOutboxRepository.java \
        src/main/java/com/listaai/infrastructure/adapter/output/persistence/EmailOutboxPersistenceAdapter.java \
        src/test/java/com/listaai/BaseIntegrationTest.java
git commit -m "feat: add email_outbox table, entity, and persistence adapter"
```

---

## Task 8: Email template renderer

**Files:**
- Create: `src/main/java/com/listaai/application/service/EmailTemplateRenderer.java`
- Test: `src/test/java/com/listaai/application/service/EmailTemplateRendererTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/listaai/application/service/EmailTemplateRendererTest.java`:

```java
package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties props = new EmailVerificationProperties(
                true, 24, 60, "https://app.test/verify-email");
        renderer = new EmailTemplateRenderer(props);
    }

    @Test
    void renders_verify_email_template() {
        OutboxRow row = new OutboxRow(
                1L, "VERIFY_EMAIL", "alice@example.com",
                "{\"token\":\"ABC123\",\"name\":\"Alice\"}",
                0);

        EmailMessage msg = renderer.render(row);

        assertThat(msg.to()).isEqualTo("alice@example.com");
        assertThat(msg.subject()).contains("verify");
        assertThat(msg.htmlBody()).contains("https://app.test/verify-email?token=ABC123");
        assertThat(msg.htmlBody()).contains("Alice");
        assertThat(msg.textBody()).contains("https://app.test/verify-email?token=ABC123");
    }

    @Test
    void throws_on_unknown_template() {
        OutboxRow row = new OutboxRow(1L, "UNKNOWN", "a@b", "{}", 0);
        assertThatThrownBy(() -> renderer.render(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.application.service.EmailTemplateRendererTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement renderer**

Create `src/main/java/com/listaai/application/service/EmailTemplateRenderer.java`:

```java
package com.listaai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmailTemplateRenderer {

    public static final String TEMPLATE_VERIFY_EMAIL = "VERIFY_EMAIL";

    private final EmailVerificationProperties verifyProps;
    private final ObjectMapper mapper = new ObjectMapper();

    public EmailTemplateRenderer(EmailVerificationProperties verifyProps) {
        this.verifyProps = verifyProps;
    }

    public EmailMessage render(OutboxRow row) {
        return switch (row.template()) {
            case TEMPLATE_VERIFY_EMAIL -> renderVerifyEmail(row);
            default -> throw new IllegalArgumentException("Unknown template: " + row.template());
        };
    }

    private EmailMessage renderVerifyEmail(OutboxRow row) {
        JsonNode payload = parse(row.payloadJson());
        String token = payload.path("token").asText();
        String name = payload.path("name").asText("");
        String verifyUrl = verifyProps.redirectBaseUrl() + "?token=" + token;

        String subject = "Please verify your email";
        String html = """
                <p>Hi %s,</p>
                <p>Please verify your email address by clicking the link below:</p>
                <p><a href="%s">Verify my email</a></p>
                <p>This link expires in %d hours.</p>
                """.formatted(escape(name), verifyUrl, verifyProps.tokenTtlHours());
        String text = """
                Hi %s,

                Please verify your email by visiting:
                %s

                This link expires in %d hours.
                """.formatted(name, verifyUrl, verifyProps.tokenTtlHours());

        return new EmailMessage(row.recipient(), subject, html, text);
    }

    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (IOException e) { throw new IllegalStateException("Malformed outbox payload", e); }
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "com.listaai.application.service.EmailTemplateRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/application/service/EmailTemplateRenderer.java \
        src/test/java/com/listaai/application/service/EmailTemplateRendererTest.java
git commit -m "feat: add email template renderer with VERIFY_EMAIL template"
```

---

## Task 9: Resend email sender adapter (WireMock-tested)

**Files:**
- Create: `src/main/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSender.java`
- Test: `src/test/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSenderTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSenderTest.java`:

```java
package com.listaai.infrastructure.adapter.output.email;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.service.exception.EmailSendException;
import com.listaai.infrastructure.config.EmailProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private WireMockServer wm;
    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        EmailProperties props = new EmailProperties(
                "noreply@test.local", "resend",
                new EmailProperties.Resend("key-xyz", "http://localhost:" + wm.port()),
                new EmailProperties.Worker(10000));
        sender = new ResendEmailSender(props, RestClient.builder());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void posts_to_resend_and_succeeds() {
        wm.stubFor(post("/emails")
                .willReturn(okJson("{\"id\":\"abc\"}")));

        sender.send(new EmailMessage("u@x.com", "sub", "<p>h</p>", "t"));

        wm.verify(postRequestedFor(urlEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer key-xyz"))
                .withRequestBody(matchingJsonPath("$.to[0]", equalTo("u@x.com")))
                .withRequestBody(matchingJsonPath("$.subject", equalTo("sub")))
                .withRequestBody(matchingJsonPath("$.html", equalTo("<p>h</p>")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("t")))
                .withRequestBody(matchingJsonPath("$.from", equalTo("noreply@test.local"))));
    }

    @Test
    void wraps_4xx_as_non_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(400).withBody("bad")));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> !((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_429_as_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_5xx_as_retryable() {
        wm.stubFor(post("/emails").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }

    @Test
    void wraps_connection_error_as_retryable() {
        wm.stop();
        assertThatThrownBy(() -> sender.send(new EmailMessage("u@x.com", "s", "h", "t")))
                .isInstanceOf(EmailSendException.class)
                .matches(e -> ((EmailSendException) e).isRetryable());
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.infrastructure.adapter.output.email.ResendEmailSenderTest"`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement `ResendEmailSender`**

Create `src/main/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSender.java`:

```java
package com.listaai.infrastructure.adapter.output.email;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import com.listaai.infrastructure.config.EmailProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend", matchIfMissing = true)
public class ResendEmailSender implements EmailSender {

    private final EmailProperties props;
    private final RestClient client;

    @Autowired
    public ResendEmailSender(EmailProperties props, RestClient.Builder builder) {
        this.props = props;
        this.client = builder.baseUrl(props.resend().baseUrl()).build();
    }

    @Override
    public void send(EmailMessage message) {
        Map<String, Object> body = Map.of(
                "from", props.fromAddress(),
                "to", List.of(message.to()),
                "subject", message.subject(),
                "html", message.htmlBody(),
                "text", message.textBody()
        );
        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.resend().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            throw new EmailSendException(
                    "Resend responded " + status + ": " + e.getResponseBodyAsString(),
                    retryable, e);
        } catch (ResourceAccessException e) {
            throw new EmailSendException("Resend unreachable: " + e.getMessage(), true, e);
        }
    }
}
```

- [ ] **Step 4: Run the test class**

Run: `./gradlew test --tests "com.listaai.infrastructure.adapter.output.email.ResendEmailSenderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSender.java \
        src/test/java/com/listaai/infrastructure/adapter/output/email/ResendEmailSenderTest.java
git commit -m "feat: add Resend email sender adapter"
```

---

## Task 10: Email outbox worker

**Files:**
- Create: `src/main/java/com/listaai/application/service/EmailOutboxWorker.java`
- Test: `src/test/java/com/listaai/application/service/EmailOutboxWorkerTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/listaai/application/service/EmailOutboxWorkerTest.java`:

```java
package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOutboxWorkerTest {

    private EmailOutboxRepository outboxRepo;
    private EmailSender sender;
    private EmailTemplateRenderer renderer;
    private Clock clock;
    private EmailOutboxWorker worker;

    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

    @BeforeEach
    void setUp() {
        outboxRepo = mock(EmailOutboxRepository.class);
        sender = mock(EmailSender.class);
        renderer = mock(EmailTemplateRenderer.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker = new EmailOutboxWorker(outboxRepo, sender, renderer, clock);
    }

    @Test
    void success_marks_sent() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));

        worker.processOutbox();

        verify(sender).send(new EmailMessage("a@b", "s", "h", "t"));
        verify(outboxRepo).markSent(1L, NOW);
    }

    @Test
    void retryable_exception_schedules_retry_with_backoff() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("boom", true)).when(sender).send(any());

        worker.processOutbox();

        ArgumentCaptor<Instant> nextCap = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepo).markRetry(eq(1L), eq(1), nextCap.capture(), eq("boom"));
        // backoff(1) = 10s
        assertThat(nextCap.getValue()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void non_retryable_exception_marks_failed_immediately() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("bad addr", false)).when(sender).send(any());

        worker.processOutbox();

        verify(outboxRepo).markFailed(1L, 1, "bad addr");
        verify(outboxRepo, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void at_max_attempts_marks_failed_even_if_retryable() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 7); // MAX=8, attempts+1=8 → fail
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("still down", true)).when(sender).send(any());

        worker.processOutbox();

        verify(outboxRepo).markFailed(1L, 8, "still down");
        verify(outboxRepo, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void backoff_sequence_matches_spec() {
        // backoff(n): 10s, 30s, 1m, 5m, 15m, 1h, 4h, 12h
        long[] expectedSeconds = {10, 30, 60, 300, 900, 3600, 14400, 43200};
        for (int attempts = 0; attempts < expectedSeconds.length; attempts++) {
            OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", attempts);
            when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
            when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
            doThrow(new EmailSendException("boom", true)).when(sender).send(any());

            worker.processOutbox();

            int expectedAttempts = attempts + 1;
            if (expectedAttempts < 8) {
                verify(outboxRepo).markRetry(eq(1L), eq(expectedAttempts),
                        eq(NOW.plusSeconds(expectedSeconds[attempts])), eq("boom"));
            }
            reset(outboxRepo, sender, renderer);
        }
    }
}
```

(Add `import static org.assertj.core.api.Assertions.assertThat;` at the top.)

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.application.service.EmailOutboxWorkerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the worker**

Create `src/main/java/com/listaai/application/service/EmailOutboxWorker.java`:

```java
package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.email-verification.enabled", havingValue = "true")
public class EmailOutboxWorker {

    private static final int BATCH_SIZE = 50;
    static final int MAX_ATTEMPTS = 8;

    // backoff for attempt 1..8: 10s, 30s, 1m, 5m, 15m, 1h, 4h, 12h
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(12)
    };

    private final EmailOutboxRepository outboxRepo;
    private final EmailSender sender;
    private final EmailTemplateRenderer renderer;
    private final Clock clock;

    public EmailOutboxWorker(EmailOutboxRepository outboxRepo,
                             EmailSender sender,
                             EmailTemplateRenderer renderer,
                             Clock clock) {
        this.outboxRepo = outboxRepo;
        this.sender = sender;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.email.worker.poll-interval-ms}")
    public void processOutbox() {
        Instant now = clock.instant();
        List<OutboxRow> batch = outboxRepo.claimPending(now, BATCH_SIZE);
        for (OutboxRow row : batch) {
            handleRow(row, now);
        }
    }

    private void handleRow(OutboxRow row, Instant now) {
        int nextAttempt = row.attempts() + 1;
        try {
            EmailMessage msg = renderer.render(row);
            sender.send(msg);
            outboxRepo.markSent(row.id(), now);
        } catch (EmailSendException e) {
            if (!e.isRetryable() || nextAttempt >= MAX_ATTEMPTS) {
                outboxRepo.markFailed(row.id(), nextAttempt, e.getMessage());
            } else {
                Instant next = now.plus(BACKOFFS[nextAttempt - 1]);
                outboxRepo.markRetry(row.id(), nextAttempt, next, e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: Run worker tests**

Run: `./gradlew test --tests "com.listaai.application.service.EmailOutboxWorkerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/application/service/EmailOutboxWorker.java \
        src/test/java/com/listaai/application/service/EmailOutboxWorkerTest.java
git commit -m "feat: add EmailOutboxWorker with retry + exponential backoff"
```

---

## Task 11: Extend `AuthUseCase` port + commands

**Files:**
- Modify: `src/main/java/com/listaai/application/port/input/AuthUseCase.java`
- Modify: `src/main/java/com/listaai/application/port/input/AuthIdentity.java`
- Create: `src/main/java/com/listaai/application/port/input/command/VerifyEmailCommand.java`
- Create: `src/main/java/com/listaai/application/port/input/command/ResendVerificationCommand.java`

Port-only changes; tests come in the next tasks that implement the behavior.

- [ ] **Step 1: Add commands**

Create `src/main/java/com/listaai/application/port/input/command/VerifyEmailCommand.java`:

```java
package com.listaai.application.port.input.command;

public record VerifyEmailCommand(String token) {}
```

Create `src/main/java/com/listaai/application/port/input/command/ResendVerificationCommand.java`:

```java
package com.listaai.application.port.input.command;

public record ResendVerificationCommand(String email) {}
```

- [ ] **Step 2: Extend `AuthIdentity` with `emailVerified`**

Edit `src/main/java/com/listaai/application/port/input/AuthIdentity.java`:

```java
package com.listaai.application.port.input;

// Returned by AuthProvider after validating external credentials
public record AuthIdentity(String email, String name, String providerUserId, boolean emailVerified) {}
```

- [ ] **Step 3: Extend `AuthUseCase` port**

Edit `src/main/java/com/listaai/application/port/input/AuthUseCase.java`:

```java
package com.listaai.application.port.input;

import com.listaai.application.port.input.command.*;

public interface AuthUseCase {
    /**
     * @return non-null AuthResult when verification is disabled; null when enabled
     *         (user must verify email before tokens are issued).
     */
    AuthResult register(RegisterCommand command);
    AuthResult loginLocal(LoginCommand command);
    AuthResult loginGoogle(GoogleAuthCommand command);
    AuthResult refresh(RefreshCommand command);
    void logout(RefreshCommand command);
    void verifyEmail(VerifyEmailCommand command);
    void resendVerification(ResendVerificationCommand command);
}
```

- [ ] **Step 4: Update `LocalAuthProvider` and `GoogleAuthProvider` to construct `AuthIdentity` with the new field**

In `LocalAuthProvider.authenticate`:

```java
return new AuthIdentity(
        userWithHash.user().email(),
        userWithHash.user().name(),
        null,                                   // no provider user ID for local auth
        userWithHash.user().verified());        // current verification state
```

In `GoogleAuthProvider.authenticate`, the boolean claim named `email_verified` comes from Google's OIDC token:

```java
Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
return new AuthIdentity(
        jwt.getClaimAsString("email"),
        jwt.getClaimAsString("name"),
        jwt.getSubject(),
        Boolean.TRUE.equals(emailVerified));
```

- [ ] **Step 5: Fix existing test constructors that build `AuthIdentity`**

Search the test tree for `new AuthIdentity(` and add a fourth arg (`true` is fine for every existing case — they're all "identity was valid").

```bash
grep -rn "new AuthIdentity(" src/test/
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew test`
Expected: PASS (the two new `AuthUseCase` methods are unimplemented — but `AuthService` already implements the interface; since the interface gained abstract methods, `AuthService` also won't compile). Proceed to next task to fix.

Actually — `AuthService` needs placeholder implementations so the build compiles at this commit boundary. Add temporary no-op bodies before running tests:

```java
@Override public void verifyEmail(VerifyEmailCommand command) { throw new UnsupportedOperationException("Task 12"); }
@Override public void resendVerification(ResendVerificationCommand command) { throw new UnsupportedOperationException("Task 13"); }
```

Then re-run tests. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/listaai/application/port/input/AuthUseCase.java \
        src/main/java/com/listaai/application/port/input/AuthIdentity.java \
        src/main/java/com/listaai/application/port/input/command/VerifyEmailCommand.java \
        src/main/java/com/listaai/application/port/input/command/ResendVerificationCommand.java \
        src/main/java/com/listaai/application/service/LocalAuthProvider.java \
        src/main/java/com/listaai/application/service/GoogleAuthProvider.java \
        src/main/java/com/listaai/application/service/AuthService.java \
        src/test/
git commit -m "feat: extend AuthUseCase port with verify/resend + emailVerified identity"
```

---

## Task 12: `AuthService.register` — flag-aware path

**Files:**
- Modify: `src/main/java/com/listaai/application/service/AuthService.java`
- Modify: `src/test/java/com/listaai/application/service/AuthServiceTest.java`

`AuthService` needs to know (a) whether the feature is enabled, (b) how to issue verification tokens, (c) how to enqueue outbox rows.

- [ ] **Step 1: Write failing tests**

Add to `AuthServiceTest.java` (inject the new dependencies and properties):

```java
@Test
void register_whenFlagOn_createsUnverifiedUser_issuesVerificationToken_enqueuesOutbox_returnsNull() {
    EmailVerificationProperties verifyProps = new EmailVerificationProperties(
            true, 24, 60, "https://app.test/verify");

    AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
            refreshTokenRepository, authProviderRegistry, jwtTokenService,
            passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("pw")).thenReturn("H");
    when(userRepository.save(any(User.class), eq("H")))
        .thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(10L, u.email(), u.name(), u.verified());
        });
    when(jwtTokenService.generateRefreshToken()).thenReturn("RAW_VTOKEN");
    when(jwtTokenService.hashRefreshToken("RAW_VTOKEN")).thenReturn("HASHED_VTOKEN");

    AuthResult result = svc.register(new RegisterCommand("a@b.com", "pw", "A"));

    assertThat(result).isNull();

    ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCap.capture(), eq("H"));
    assertThat(userCap.getValue().verified()).isFalse();

    verify(verifyTokenRepository).save(eq(10L), eq("HASHED_VTOKEN"), any(Instant.class), any(Instant.class));
    verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("a@b.com"), contains("RAW_VTOKEN"), any(Instant.class));
    verifyNoInteractions(refreshTokenRepository);
}

@Test
void register_whenFlagOff_existingFastPathUnchanged() {
    EmailVerificationProperties verifyProps = new EmailVerificationProperties(
            false, 24, 60, "https://app.test/verify");

    AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
            refreshTokenRepository, authProviderRegistry, jwtTokenService,
            passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("pw")).thenReturn("H");
    when(userRepository.save(any(User.class), eq("H")))
        .thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(10L, u.email(), u.name(), u.verified());
        });
    when(jwtTokenService.generateAccessToken(any())).thenReturn("ACCESS");
    when(jwtTokenService.generateRefreshToken()).thenReturn("REFRESH");
    when(jwtTokenService.hashRefreshToken("REFRESH")).thenReturn("RH");

    AuthResult result = svc.register(new RegisterCommand("a@b.com", "pw", "A"));

    assertThat(result).isNotNull();
    assertThat(result.accessToken()).isEqualTo("ACCESS");
    verify(refreshTokenRepository).save(eq(10L), eq("RH"), any(Instant.class));
    verifyNoInteractions(verifyTokenRepository, outboxRepo);
}
```

Add `@BeforeEach` setup of `fixedClock`, `verifyTokenRepository` (new mock), `outboxRepo` (new mock).

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: FAIL — new `AuthService` constructor args don't exist.

- [ ] **Step 3: Implement flag-aware `register`**

In `AuthService.java`:

```java
// new fields
private final EmailVerificationTokenRepository verifyTokenRepository;
private final EmailOutboxRepository outboxRepository;
private final EmailVerificationProperties verificationProperties;
private final ObjectMapper objectMapper = new ObjectMapper();

// constructor — add three new parameters at the end
public AuthService(
        UserRepository userRepository,
        OAuthIdentityRepository oAuthIdentityRepository,
        RefreshTokenRepository refreshTokenRepository,
        AuthProviderRegistry authProviderRegistry,
        JwtTokenService jwtTokenService,
        PasswordEncoder passwordEncoder,
        @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays,
        Clock clock,
        EmailVerificationTokenRepository verifyTokenRepository,
        EmailOutboxRepository outboxRepository,
        EmailVerificationProperties verificationProperties) {
    // ... existing assignments ...
    this.verifyTokenRepository = verifyTokenRepository;
    this.outboxRepository = outboxRepository;
    this.verificationProperties = verificationProperties;
}

@Override
@Transactional
public AuthResult register(RegisterCommand command) {
    if (userRepository.findByEmail(command.email()).isPresent()) {
        throw new IllegalStateException("Email already registered: " + command.email());
    }
    String hash = passwordEncoder.encode(command.password());
    boolean verificationEnabled = verificationProperties.enabled();
    User user = userRepository.save(
            new User(null, command.email(), command.name(), !verificationEnabled), hash);

    if (!verificationEnabled) {
        return issueTokens(user);
    }
    issueVerificationEmail(user);
    return null;
}

private void issueVerificationEmail(User user) {
    Instant now = clock.instant();
    String rawToken = jwtTokenService.generateRefreshToken();              // reuse opaque-token generator
    String hash = jwtTokenService.hashRefreshToken(rawToken);
    Instant expiresAt = now.plus(verificationProperties.tokenTtlHours(), ChronoUnit.HOURS);
    verifyTokenRepository.save(user.id(), hash, expiresAt, now);

    String payload;
    try {
        payload = objectMapper.writeValueAsString(Map.of(
                "token", rawToken,
                "name", user.name()));
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to serialize verify payload", e);
    }
    outboxRepository.enqueue("VERIFY_EMAIL", user.email(), payload, now);
}
```

Imports to add: `ObjectMapper`, `JsonProcessingException`, `Map`, `ChronoUnit`, the new repo ports, `EmailVerificationProperties`.

- [ ] **Step 4: Run the two new tests**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/listaai/application/service/AuthService.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: flag-aware register — creates unverified user + outbox row when enabled"
```

---

## Task 13: `AuthService.verifyEmail` + controller endpoint

**Files:**
- Modify: `src/main/java/com/listaai/application/service/AuthService.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/VerifyEmailRequest.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/RegisterPendingResponse.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/listaai/application/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing unit tests for `verifyEmail`**

Add to `AuthServiceTest`:

```java
@Test
void verifyEmail_setsUserVerified_andMarksTokenUsed() {
    EmailVerificationToken token = new EmailVerificationToken(
            1L, 42L, NOW.plusSeconds(3600), null, null, NOW);
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
    when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

    authService.verifyEmail(new VerifyEmailCommand("RAW"));

    verify(userRepository).setVerified(42L, true);
    verify(verifyTokenRepository).markUsed(1L, NOW);
}

@Test
void verifyEmail_idempotent_whenAlreadyUsed_noStateChange() {
    EmailVerificationToken token = new EmailVerificationToken(
            1L, 42L, NOW.plusSeconds(3600), NOW.minusSeconds(60), null, NOW.minusSeconds(120));
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
    when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

    authService.verifyEmail(new VerifyEmailCommand("RAW"));

    verify(userRepository, never()).setVerified(anyLong(), anyBoolean());
    verify(verifyTokenRepository, never()).markUsed(anyLong(), any());
}

@Test
void verifyEmail_unknownToken_throwsInvalidToken() {
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
    when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailCommand("RAW")))
            .isInstanceOf(InvalidVerificationTokenException.class);
}

@Test
void verifyEmail_expired_throwsExpired() {
    EmailVerificationToken token = new EmailVerificationToken(
            1L, 42L, NOW.minusSeconds(60), null, null, NOW.minusSeconds(3600));
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
    when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailCommand("RAW")))
            .isInstanceOf(VerificationTokenExpiredException.class);
}

@Test
void verifyEmail_revoked_throwsSuperseded() {
    EmailVerificationToken token = new EmailVerificationToken(
            1L, 42L, NOW.plusSeconds(3600), null, NOW.minusSeconds(60), NOW.minusSeconds(120));
    when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
    when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailCommand("RAW")))
            .isInstanceOf(VerificationTokenSupersededException.class);
}
```

- [ ] **Step 2: Run — expect compile failure (exceptions don't exist)**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: FAIL.

- [ ] **Step 3: Create the three new exceptions (extend the existing exception package)**

Create these files:

`src/main/java/com/listaai/application/service/exception/InvalidVerificationTokenException.java`:
```java
package com.listaai.application.service.exception;

public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() { super("Invalid verification token"); }
}
```

`src/main/java/com/listaai/application/service/exception/VerificationTokenExpiredException.java`:
```java
package com.listaai.application.service.exception;

public class VerificationTokenExpiredException extends RuntimeException {
    public VerificationTokenExpiredException() { super("Verification token expired"); }
}
```

`src/main/java/com/listaai/application/service/exception/VerificationTokenSupersededException.java`:
```java
package com.listaai.application.service.exception;

public class VerificationTokenSupersededException extends RuntimeException {
    public VerificationTokenSupersededException() {
        super("Verification token superseded by a newer one");
    }
}
```

- [ ] **Step 4: Implement `verifyEmail` in `AuthService`**

Replace the placeholder:

```java
@Override
@Transactional
public void verifyEmail(VerifyEmailCommand command) {
    String hash = jwtTokenService.hashRefreshToken(command.token());
    EmailVerificationToken token = verifyTokenRepository.findByTokenHash(hash)
            .orElseThrow(InvalidVerificationTokenException::new);

    Instant now = clock.instant();
    if (token.isRevoked()) throw new VerificationTokenSupersededException();
    if (token.isExpired(now)) throw new VerificationTokenExpiredException();
    if (token.isUsed()) return; // idempotent: already verified

    userRepository.setVerified(token.userId(), true);
    verifyTokenRepository.markUsed(token.id(), now);
}
```

- [ ] **Step 5: Add DTO + controller endpoint**

Create `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/VerifyEmailRequest.java`:

```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record VerifyEmailRequest(String token) {}
```

Add to `AuthController`:

```java
@PostMapping("/verify-email")
@Operation(summary = "Verify an email address",
           description = "Consumes a verification token previously emailed to the user.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Verified (or already verified — idempotent)"),
    @ApiResponse(responseCode = "400", description = "Token is invalid", content = @Content),
    @ApiResponse(responseCode = "410", description = "Token expired or superseded", content = @Content)
})
public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest request) {
    authUseCase.verifyEmail(new VerifyEmailCommand(request.token()));
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 6: Map the exceptions in `GlobalExceptionHandler`**

Add three handlers:

```java
@ExceptionHandler(InvalidVerificationTokenException.class)
public ProblemDetail handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
}

@ExceptionHandler(VerificationTokenExpiredException.class)
public ProblemDetail handleVerificationTokenExpired(VerificationTokenExpiredException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
}

@ExceptionHandler(VerificationTokenSupersededException.class)
public ProblemDetail handleVerificationTokenSuperseded(VerificationTokenSupersededException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/listaai/application/service/AuthService.java \
        src/main/java/com/listaai/application/service/exception/*.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/VerifyEmailRequest.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: add /v1/auth/verify-email endpoint with idempotent semantics"
```

---

## Task 14: `AuthService.resendVerification` + controller endpoint

**Files:**
- Modify: `src/main/java/com/listaai/application/service/AuthService.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ResendVerificationRequest.java`
- Create: `src/main/java/com/listaai/application/service/exception/VerificationCooldownException.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java`

- [ ] **Step 1: Write failing tests**

Add to `AuthServiceTest`:

```java
@Test
void resend_forUnknownEmail_returnsSilently() {
    when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
    authService.resendVerification(new ResendVerificationCommand("x@x.com"));
    verifyNoInteractions(verifyTokenRepository, outboxRepo);
}

@Test
void resend_forAlreadyVerifiedUser_returnsSilently() {
    when(userRepository.findByEmail("v@x.com"))
        .thenReturn(Optional.of(new User(1L, "v@x.com", "V", true)));
    authService.resendVerification(new ResendVerificationCommand("v@x.com"));
    verifyNoInteractions(verifyTokenRepository, outboxRepo);
}

@Test
void resend_withinCooldown_throws429() {
    when(userRepository.findByEmail("u@x.com"))
        .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
    EmailVerificationToken recent = new EmailVerificationToken(
            9L, 1L, NOW.plusSeconds(3600), null, null, NOW.minusSeconds(30));
    when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.of(recent));

    assertThatThrownBy(() -> authService.resendVerification(new ResendVerificationCommand("u@x.com")))
            .isInstanceOf(VerificationCooldownException.class);
    verifyNoInteractions(outboxRepo);
}

@Test
void resend_afterCooldown_revokesOldAndIssuesNew() {
    when(userRepository.findByEmail("u@x.com"))
        .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
    EmailVerificationToken old = new EmailVerificationToken(
            9L, 1L, NOW.plusSeconds(3600), null, null, NOW.minusSeconds(120));
    when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.of(old));
    when(jwtTokenService.generateRefreshToken()).thenReturn("NEW_RAW");
    when(jwtTokenService.hashRefreshToken("NEW_RAW")).thenReturn("NEW_HASH");

    authService.resendVerification(new ResendVerificationCommand("u@x.com"));

    verify(verifyTokenRepository).markRevoked(9L, NOW);
    verify(verifyTokenRepository).save(eq(1L), eq("NEW_HASH"), any(Instant.class), any(Instant.class));
    verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("u@x.com"), contains("NEW_RAW"), any(Instant.class));
}

@Test
void resend_firstTime_noPriorToken_issuesNew() {
    when(userRepository.findByEmail("u@x.com"))
        .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
    when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());
    when(jwtTokenService.generateRefreshToken()).thenReturn("NEW_RAW");
    when(jwtTokenService.hashRefreshToken("NEW_RAW")).thenReturn("NEW_HASH");

    authService.resendVerification(new ResendVerificationCommand("u@x.com"));

    verify(verifyTokenRepository, never()).markRevoked(anyLong(), any());
    verify(verifyTokenRepository).save(eq(1L), eq("NEW_HASH"), any(Instant.class), any(Instant.class));
    verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("u@x.com"), contains("NEW_RAW"), any(Instant.class));
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: FAIL.

- [ ] **Step 3: Create `VerificationCooldownException`**

```java
package com.listaai.application.service.exception;

public class VerificationCooldownException extends RuntimeException {
    public VerificationCooldownException() { super("Please wait before requesting another verification email"); }
}
```

- [ ] **Step 4: Implement `resendVerification`**

Replace placeholder in `AuthService`:

```java
@Override
@Transactional
public void resendVerification(ResendVerificationCommand command) {
    Optional<User> maybeUser = userRepository.findByEmail(command.email());
    if (maybeUser.isEmpty()) return;
    User user = maybeUser.get();
    if (user.verified()) return;

    Instant now = clock.instant();
    Optional<EmailVerificationToken> latest = verifyTokenRepository.findLatestByUserId(user.id());

    latest.ifPresent(t -> {
        long elapsed = now.getEpochSecond() - t.createdAt().getEpochSecond();
        if (elapsed < verificationProperties.resendCooldownSeconds()) {
            throw new VerificationCooldownException();
        }
        if (!t.isUsed() && !t.isRevoked()) {
            verifyTokenRepository.markRevoked(t.id(), now);
        }
    });

    issueVerificationEmail(user);
}
```

Note: `EmailVerificationToken` record now needs `createdAt` — already added in Task 6.

- [ ] **Step 5: Create DTO + controller endpoint**

`src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ResendVerificationRequest.java`:
```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record ResendVerificationRequest(String email) {}
```

In `AuthController`:

```java
@PostMapping("/resend-verification")
@Operation(summary = "Resend verification email",
           description = "Sends a fresh verification email. Always returns 200 to avoid account enumeration.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Request accepted"),
    @ApiResponse(responseCode = "429", description = "Cooldown — try again shortly", content = @Content)
})
public ResponseEntity<Void> resendVerification(@RequestBody ResendVerificationRequest request) {
    authUseCase.resendVerification(new ResendVerificationCommand(request.email()));
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 6: Map cooldown exception in `GlobalExceptionHandler`**

```java
@ExceptionHandler(VerificationCooldownException.class)
public ProblemDetail handleCooldown(VerificationCooldownException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/listaai/application/service/AuthService.java \
        src/main/java/com/listaai/application/service/exception/VerificationCooldownException.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/ResendVerificationRequest.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: add /v1/auth/resend-verification with cooldown + old-token revocation"
```

---

## Task 15: Login gate — 403 when flag on and user unverified

**Files:**
- Modify: `src/main/java/com/listaai/application/service/AuthService.java`
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/listaai/application/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add to `AuthServiceTest` (with flag-on and flag-off variants):

```java
@Test
void loginLocal_whenFlagOn_andUnverifiedUser_throwsEmailNotVerified() {
    EmailVerificationProperties verifyProps = new EmailVerificationProperties(
            true, 24, 60, "https://app.test/verify");
    AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
            refreshTokenRepository, authProviderRegistry, jwtTokenService,
            passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

    User unverified = new User(1L, "u@x.com", "U", false);
    AuthProvider localProvider = mock(AuthProvider.class);
    when(authProviderRegistry.get("local")).thenReturn(localProvider);
    when(localProvider.authenticate(any())).thenReturn(new AuthIdentity("u@x.com", "U", null, false));
    when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(unverified));

    assertThatThrownBy(() -> svc.loginLocal(new LoginCommand("u@x.com", "pw")))
            .isInstanceOf(EmailNotVerifiedException.class);
    verifyNoInteractions(refreshTokenRepository);
}

@Test
void loginLocal_whenFlagOff_andUnverifiedUser_succeeds() {
    EmailVerificationProperties verifyProps = new EmailVerificationProperties(
            false, 24, 60, "https://app.test/verify");
    AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
            refreshTokenRepository, authProviderRegistry, jwtTokenService,
            passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

    User unverified = new User(1L, "u@x.com", "U", false);
    AuthProvider localProvider = mock(AuthProvider.class);
    when(authProviderRegistry.get("local")).thenReturn(localProvider);
    when(localProvider.authenticate(any())).thenReturn(new AuthIdentity("u@x.com", "U", null, false));
    when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(unverified));
    when(jwtTokenService.generateAccessToken(any())).thenReturn("ACCESS");
    when(jwtTokenService.generateRefreshToken()).thenReturn("RT");
    when(jwtTokenService.hashRefreshToken("RT")).thenReturn("RTH");

    AuthResult result = svc.loginLocal(new LoginCommand("u@x.com", "pw"));
    assertThat(result).isNotNull();
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew test --tests "com.listaai.application.service.AuthServiceTest"`
Expected: FAIL.

- [ ] **Step 3: Add the gate to `loginLocal` and `loginGoogle`**

In `AuthService.loginLocal`:

```java
@Override
@Transactional
public AuthResult loginLocal(LoginCommand command) {
    AuthProvider provider = authProviderRegistry.get("local");
    AuthIdentity identity = provider.authenticate(command);
    User user = userRepository.findByEmail(identity.email())
            .orElseThrow(() -> new BadCredentialsException("User not found"));
    if (verificationProperties.enabled() && !user.verified()) {
        throw new EmailNotVerifiedException();
    }
    return issueTokens(user);
}
```

For `loginGoogle`, the new-user path should set `verified` from `identity.emailVerified()`, and the existing-user path should still gate on `user.verified()`:

```java
@Override
@Transactional
public AuthResult loginGoogle(GoogleAuthCommand command) {
    AuthProvider provider = authProviderRegistry.get("google");
    AuthIdentity identity = provider.authenticate(command);

    Optional<OAuthIdentity> existingIdentity =
            oAuthIdentityRepository.findByProviderAndProviderUserId("google", identity.providerUserId());

    User user;
    if (existingIdentity.isPresent()) {
        user = userRepository.findById(existingIdentity.get().userId())
                .orElseThrow(() -> new IllegalStateException("User not found for OAuth identity"));
    } else {
        user = userRepository.findByEmail(identity.email())
                .orElseGet(() -> userRepository.save(
                        new User(null, identity.email(), identity.name(),
                                identity.emailVerified() || !verificationProperties.enabled()),
                        null));
        oAuthIdentityRepository.save(
                new OAuthIdentity(null, user.id(), "google", identity.providerUserId()));
    }
    if (verificationProperties.enabled() && !user.verified()) {
        throw new EmailNotVerifiedException();
    }
    return issueTokens(user);
}
```

- [ ] **Step 4: Map `EmailNotVerifiedException` → 403 in `GlobalExceptionHandler`**

```java
@ExceptionHandler(EmailNotVerifiedException.class)
public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
}
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/listaai/application/service/AuthService.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/GlobalExceptionHandler.java \
        src/test/java/com/listaai/application/service/AuthServiceTest.java
git commit -m "feat: gate login with 403 when email-verification enabled and user unverified"
```

---

## Task 16: `register` controller returns 202 when flag on

When `AuthService.register` returns `null` (flag on), the controller needs a different HTTP status + body.

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java`
- Create: `src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/RegisterPendingResponse.java`

- [ ] **Step 1: Add the pending response DTO**

```java
package com.listaai.infrastructure.adapter.input.rest.dto;

public record RegisterPendingResponse(String message) {}
```

- [ ] **Step 2: Change the controller signature to `ResponseEntity<?>` and branch on result**

```java
@PostMapping("/register")
@Operation(summary = "Register a new user",
           description = """
                   Creates a new user account. When email-verification is enabled, \
                   returns 202 Accepted and sends a verification email; no tokens \
                   are issued until the user verifies. When disabled, returns 201 \
                   with tokens (legacy behavior).""")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "User registered (flag off) — tokens returned",
        content = @Content(schema = @Schema(implementation = TokenResponse.class))),
    @ApiResponse(responseCode = "202", description = "User registered (flag on) — verification email sent",
        content = @Content(schema = @Schema(implementation = RegisterPendingResponse.class))),
    @ApiResponse(responseCode = "409", description = "Email already registered",
        content = @Content)
})
public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    AuthResult result = authUseCase.register(mapper.toCommand(request));
    if (result == null) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RegisterPendingResponse("Check your email to verify your account."));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
}
```

- [ ] **Step 3: Run tests — confirm `AuthControllerIT` (flag off, default) still returns 201**

Run: `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.AuthControllerIT"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/listaai/infrastructure/adapter/input/rest/dto/RegisterPendingResponse.java \
        src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java
git commit -m "feat: return 202 on /register when email verification is enabled"
```

---

## Task 17: Integration test — full flow (flag on, WireMocking Resend)

**Files:**
- Create: `src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationIT.java`

- [ ] **Step 1: Write the integration test**

Create `src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationIT.java`:

```java
package com.listaai.infrastructure.adapter.input.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.listaai.BaseIntegrationTest;
import com.listaai.application.service.EmailOutboxWorker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.email-verification.enabled=true",
        "app.email.resend.base-url=http://localhost:9091",
        "app.email.resend.api-key=test-key",
        "app.email.from-address=noreply@test.local"
})
class EmailVerificationIT extends BaseIntegrationTest {

    static WireMockServer wiremock;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmailOutboxWorker worker;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(9091);
        wiremock.start();
    }

    @AfterAll
    static void stopWiremock() { wiremock.stop(); }

    @BeforeEach
    void resetWiremock() { wiremock.resetAll(); }

    @Test
    void full_flow_register_verify_login() {
        wiremock.stubFor(post("/emails").willReturn(okJson("{\"id\":\"r1\"}")));

        // 1. register — expect 202, no tokens
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\",\"name\":\"Alice\"}")
                .post("/v1/auth/register")
                .then().statusCode(202);

        // 2. login before verify — 403
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(403);

        // 3. run worker → WireMock sees the POST /emails call
        worker.processOutbox();
        wiremock.verify(postRequestedFor(urlEqualTo("/emails"))
                .withRequestBody(matchingJsonPath("$.to[0]", equalTo("alice@example.com"))));

        // 4. read the raw token from outbox payload JSON (store as HASH in DB; raw is in the payload)
        String payload = jdbc.queryForObject(
                "SELECT payload_json FROM email_outbox WHERE recipient = 'alice@example.com' ORDER BY id DESC LIMIT 1",
                String.class);
        String rawToken = payload.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // 5. verify-email — 200
        given().body(Map.of("token", rawToken))
                .post("/v1/auth/verify-email")
                .then().statusCode(200);

        // 6. verify twice — still 200 (idempotent)
        given().body(Map.of("token", rawToken))
                .post("/v1/auth/verify-email")
                .then().statusCode(200);

        // 7. login now — 200 + tokens
        given().body("{\"email\":\"alice@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(200)
                .body("accessToken", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void resend_respects_cooldown() {
        wiremock.stubFor(post("/emails").willReturn(okJson("{\"id\":\"r1\"}")));

        given().body("{\"email\":\"bob@example.com\",\"password\":\"Secret123!\",\"name\":\"Bob\"}")
                .post("/v1/auth/register").then().statusCode(202);

        given().body(Map.of("email", "bob@example.com"))
                .post("/v1/auth/resend-verification")
                .then().statusCode(429);
    }

    @Test
    void resend_for_unknown_email_returns_200_without_enumeration() {
        given().body(Map.of("email", "ghost@example.com"))
                .post("/v1/auth/resend-verification")
                .then().statusCode(200);
    }

    @Test
    void resend_server_5xx_keeps_row_pending_for_retry() {
        wiremock.stubFor(post("/emails").willReturn(aResponse().withStatus(503)));

        given().body("{\"email\":\"carol@example.com\",\"password\":\"Secret123!\",\"name\":\"Carol\"}")
                .post("/v1/auth/register").then().statusCode(202);

        worker.processOutbox();
        int attempts = jdbc.queryForObject(
                "SELECT attempts FROM email_outbox WHERE recipient = 'carol@example.com'", Integer.class);
        String status = jdbc.queryForObject(
                "SELECT status FROM email_outbox WHERE recipient = 'carol@example.com'", String.class);
        assertThat(attempts).isEqualTo(1);
        assertThat(status).isEqualTo("PENDING");
    }
}
```

- [ ] **Step 2: Run the IT**

Run: `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.EmailVerificationIT"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationIT.java
git commit -m "test: end-to-end integration test for email verification flow"
```

---

## Task 18: Integration test — flag off parity

Proves `EmailVerificationDisabledIT` keeps pre-feature behavior.

**Files:**
- Create: `src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationDisabledIT.java`

- [ ] **Step 1: Write the test**

```java
package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.given;

@TestPropertySource(properties = "app.email-verification.enabled=false")
class EmailVerificationDisabledIT extends BaseIntegrationTest {

    @Test
    void register_returns_201_with_tokens_when_flag_off() {
        given().body("{\"email\":\"dave@example.com\",\"password\":\"Secret123!\",\"name\":\"Dave\"}")
                .post("/v1/auth/register")
                .then().statusCode(201)
                .body("accessToken", org.hamcrest.Matchers.notNullValue())
                .body("refreshToken", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void login_succeeds_without_verification_when_flag_off() {
        given().body("{\"email\":\"erin@example.com\",\"password\":\"Secret123!\",\"name\":\"Erin\"}")
                .post("/v1/auth/register").then().statusCode(201);

        given().body("{\"email\":\"erin@example.com\",\"password\":\"Secret123!\"}")
                .post("/v1/auth/login")
                .then().statusCode(200);
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.EmailVerificationDisabledIT"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/listaai/infrastructure/adapter/input/rest/EmailVerificationDisabledIT.java
git commit -m "test: integration test confirms flag-off parity with legacy behavior"
```

---

## Task 19: Deploy-doc update + final verification

**Files:**
- Modify: `docs/superpowers/specs/2026-04-19-render-neon-deploy-design.md` (env var list)

The new secrets / env vars must be called out alongside the existing ones.

- [ ] **Step 1: Append new env vars to the Render env-var list**

Edit `docs/superpowers/specs/2026-04-19-render-neon-deploy-design.md`. In the "env vars" subsection, add:

```yaml
  - RESEND_API_KEY                                  # Resend API key
  - EMAIL_FROM                                      # from-address (e.g. noreply@lista-ai.com)
  - EMAIL_VERIFICATION_ENABLED                      # "true" in prod, "false" in tests/dev
  - VERIFICATION_REDIRECT_BASE_URL                  # https://app.lista-ai.com/verify-email
```

- [ ] **Step 2: Run full build**

Run: `./gradlew clean build`
Expected: PASS — all tests green, JaCoCo coverage verification passes (≥ 90% on the non-excluded classes).

If JaCoCo fails, add targeted tests to the uncovered branches in `AuthService` or adapters — do not lower the threshold.

- [ ] **Step 3: Commit + push branch**

```bash
git add docs/superpowers/specs/2026-04-19-render-neon-deploy-design.md
git commit -m "docs: list new email verification env vars for Render deploy"
git push -u origin HEAD
```

- [ ] **Step 4: Open a PR**

Run:

```bash
gh pr create --title "feat: email verification with Resend (feature-toggled)" --body "$(cat <<'EOF'
## Summary
- Email verification via Resend, feature-toggled with `app.email-verification.enabled`
- Hard gate on login; transactional outbox + async worker for fail-safe delivery
- `EmailSender` port makes the vendor swappable

## Test plan
- [ ] `./gradlew test` passes
- [ ] `EmailVerificationIT` covers register → verify → login end-to-end
- [ ] `EmailVerificationDisabledIT` proves legacy parity when flag is off
- [ ] Manual test against real Resend in staging after secrets are set

See [design spec](docs/superpowers/specs/2026-04-23-email-verification-design.md).
EOF
)"
```

---

## Self-Review

**Spec coverage (every numbered Decision in the spec has at least one task):**

| Spec item | Task(s) |
|---|---|
| Hard gate on login (Decision 1) | 15 |
| Feature toggle (Decision 2) | 4 (property class), 12/14/15 (consumers) |
| Universal Link redirect (Decision 3) | 4 (config), 8 (renderer uses `redirect-base-url`) |
| Transactional outbox + async worker (Decision 4) | 7, 10, 12, 14 |
| Resend = invalidate old + issue new (Decision 5) | 14 |
| 60s cooldown + 24h TTL (Decision 6) | 12 (TTL), 14 (cooldown) |
| Idempotent verify (Decision 7) | 13 |
| `users.verified` backfilled TRUE | 2 |
| `email_verification_tokens` schema | 6 |
| `email_outbox` schema + partial index | 7 |
| `EmailSender` port | 5 |
| Resend adapter | 9 |
| `EmailOutboxWorker` with backoff | 10 |
| Google OAuth passes `email_verified` | 11 (identity field), 15 (new-user path) |
| Security: no enumeration, no plaintext logging | 14 (no-enum), covered throughout (no token fields logged) |
| Test with WireMock; BaseIntegrationTest TRUNCATE | 6, 7, 17, 18 |
| Deploy env vars updated | 19 |

**Placeholder scan:** no "TBD", "TODO", "implement later", or "handle edge cases" placeholders remain. Every code step has complete code.

**Type consistency:**
- `User(Long, String, String, boolean)` used throughout.
- `AuthIdentity(String, String, String, boolean)` used in Tasks 11, 15, 17.
- `EmailVerificationToken(Long, Long, Instant, Instant, Instant, Instant)` in Tasks 6, 13, 14.
- `EmailMessage(String, String, String, String)` in Tasks 5, 8, 9, 10.
- `OutboxRow(Long, String, String, String, int)` in Tasks 7, 8, 10.
- `save(Long, String, Instant, Instant)` on `EmailVerificationTokenRepository` in Task 6 and used in 12, 14.
- `enqueue(String, String, String, Instant)` on `EmailOutboxRepository` in Task 7 and used in 12, 14.
- `markUsed`, `markRevoked`, `markSent`, `markRetry`, `markFailed` method names consistent between defs (6, 7) and callers (10, 13).

Consistency confirmed.
