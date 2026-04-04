# OAuth2 Authentication & User Ownership — Design Spec

**Date:** 2026-04-03  
**Status:** Approved

---

## Context

Lista AI currently has no authentication. All endpoints are public and lists have no owner. This spec covers adding:
1. Google OAuth2 sign-in for mobile/SPA clients
2. Local email + password registration and login
3. User ownership of shopping lists (n:n, enabling future sharing)
4. An Abstract Auth Provider pattern for easy addition of future providers (GitHub, Apple, etc.)

The API is consumed by a mobile/SPA client that handles the OAuth2 redirect flow itself and sends a Google ID token to the backend.

---

## Architecture Overview

**Pattern:** Resource Server + Custom JWT  
The client (mobile/SPA) completes the Google OAuth2 flow independently and sends the resulting Google ID token to `POST /v1/auth/google`. The backend validates the token, finds or creates the user, and issues its own short-lived JWT access token plus an opaque refresh token. All protected endpoints require `Authorization: Bearer <jwt>`.

This keeps the backend stateless and provider-agnostic. Local email+password auth follows the same response shape.

---

## Database Schema

New Liquibase migration files (numbered sequentially after existing 002):

```sql
-- 003: users
CREATE TABLE users (
  id           BIGSERIAL PRIMARY KEY,
  email        TEXT UNIQUE NOT NULL,
  name         TEXT NOT NULL,
  password_hash TEXT,          -- null for OAuth-only users
  created_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- 004: oauth_identities (one row per provider per user)
CREATE TABLE oauth_identities (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider         TEXT NOT NULL,            -- "google", "github", etc.
  provider_user_id TEXT NOT NULL,
  UNIQUE (provider, provider_user_id)
);

-- 005: refresh_tokens
CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,           -- SHA-256 of opaque token
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at TIMESTAMP NOT NULL,
  revoked    BOOLEAN NOT NULL DEFAULT FALSE
);

-- 006: user_shopping_list (n:n ownership, future sharing)
CREATE TABLE user_shopping_list (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  list_id BIGINT NOT NULL REFERENCES list(id) ON DELETE CASCADE,
  role    TEXT NOT NULL DEFAULT 'owner',     -- "owner" | "collaborator"
  PRIMARY KEY (user_id, list_id)
);
```

**Why `oauth_identities` is separate:** A user who registers with email+password can later link Google (same email), and a Google user can add a password. Merging these into nullable columns on `users` creates ambiguity about account state.

---

## Domain Model

New records in `domain/model/` (no external dependencies):

```java
public record User(Long id, String email, String name) {}
public record OAuthIdentity(Long id, Long userId, String provider, String providerUserId) {}
```

`passwordHash` is a persistence concern (not in the domain record). `User` has no knowledge of which providers are linked.

---

## Auth Provider Abstraction

```
application/port/input/
  AuthProvider.java          ← interface
  AuthProviderRegistry.java  ← interface (findProvider(name))

application/service/
  AuthProviderRegistryImpl.java  ← Map<String, AuthProvider>, populated via Spring injection
  LocalAuthProvider.java         ← implements AuthProvider
  GoogleAuthProvider.java        ← implements AuthProvider
```

```java
public interface AuthProvider {
    String providerName();                          // "google", "local"
    AuthResult authenticate(AuthCommand command);   // throws AuthenticationException if invalid
}
```

`GoogleAuthProvider` validates the Google ID token by fetching Google's JWKS endpoint (`https://www.googleapis.com/oauth2/v3/certs`) and verifying the JWT signature, audience, and expiry. The JWKS URL is configurable (`app.auth.google.jwks-uri`) so tests can point it at WireMock.

Adding a new provider: implement `AuthProvider`, annotate with `@Component` — Spring auto-registers it.

---

## Application Layer

```
application/port/input/
  AuthUseCase.java       ← registerLocal(), loginLocal(), loginGoogle(), refresh(), logout()
  UserService.java       ← findById(), findByEmail()
  command/
    RegisterCommand.java       ← email, password, name
    LoginCommand.java          ← email, password
    GoogleAuthCommand.java     ← idToken (raw string from client)
    RefreshCommand.java        ← refreshToken (opaque string)

application/port/output/
  UserRepository.java              ← findByEmail(), save()
  OAuthIdentityRepository.java     ← findByProviderAndProviderId(), save()
  RefreshTokenRepository.java      ← save(), findByTokenHash(), revokeAllForUser()

application/service/
  AuthService.java           ← implements AuthUseCase
  UserServiceImpl.java       ← implements UserService
```

`AuthService` coordinates: call `AuthProvider` → find/create user → issue tokens. It has no knowledge of HTTP or persistence details.

---

## Token Strategy

**Access token (JWT, HS256):**
- TTL: 15 minutes
- Claims: `sub` (userId), `email`, `iat`, `exp`
- Secret from: `app.jwt.secret` (min 256-bit, externalized — never hardcoded)
- Validated by Spring Security's Resource Server JWT decoder on every request

**Refresh token (opaque):**
- 256-bit random bytes, URL-safe Base64 encoded
- Stored as SHA-256 hash in `refresh_tokens` table
- TTL: 7 days
- Single-use with rotation: each `POST /v1/auth/refresh` revokes the old token and issues a new one
- `POST /v1/auth/logout` revokes the current refresh token

**Password hashing:** BCrypt with strength 12.

---

## Auth Endpoints

All under `/v1/auth/*`, no authentication required:

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/v1/auth/register` | `{email, password, name}` | `TokenResponse` |
| POST | `/v1/auth/login` | `{email, password}` | `TokenResponse` |
| POST | `/v1/auth/google` | `{idToken}` | `TokenResponse` |
| POST | `/v1/auth/refresh` | `{refreshToken}` | `TokenResponse` |
| POST | `/v1/auth/logout` | `{refreshToken}` | 204 No Content |

`TokenResponse`:
```json
{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900 }
```

---

## Infrastructure Layer

```
infrastructure/adapter/
  input/rest/
    AuthController.java
    dto/  RegisterRequest, LoginRequest, GoogleAuthRequest, RefreshRequest, TokenResponse
    mapper/AuthRestMapper.java

  output/persistence/
    UserPersistenceAdapter.java
    OAuthIdentityPersistenceAdapter.java
    RefreshTokenPersistenceAdapter.java
    entity/  UserEntity, OAuthIdentityEntity, RefreshTokenEntity
    mapper/  UserPersistenceMapper, OAuthIdentityPersistenceMapper, RefreshTokenPersistenceMapper
    repository/  UserJpaRepository, OAuthIdentityJpaRepository, RefreshTokenJpaRepository

  security/
    SecurityConfig.java       ← SecurityFilterChain: permit /v1/auth/**, require auth everywhere else
    JwtTokenService.java      ← issue(), validate() — used by AuthService and SecurityConfig
```

`SecurityConfig` uses Spring Security's `oauth2ResourceServer(jwt -> ...)` with a custom `JwtDecoder` that reads the secret from config. It does NOT use `oauth2Login()` (that's for server-side redirect flows).

---

## List Ownership

Ownership is enforced at the list operation level, not at auth time:

- `POST /v1/lists`: extracts `userId` from the JWT, creates the list, inserts a `user_shopping_list` row with `role = "owner"`.
- `GET /v1/lists`: returns only lists where `user_shopping_list.user_id = :userId`.
- `DELETE /v1/lists/{id}`: verifies `user_shopping_list` ownership before deletion.

`ListService` interface gains a `userId` parameter on affected methods. Commands (`CreateListCommand`) gain a `userId` field. The REST controller extracts `userId` from the `Authentication` object injected by Spring Security — never from request body or path.

---

## Build Dependencies to Add

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.security:spring-security-oauth2-resource-server")
implementation("org.springframework.security:spring-security-oauth2-jose")  // JWT support

// Testing
testImplementation("org.wiremock:wiremock-standalone:3.12.1")  // mock Google JWKS
testImplementation("org.springframework.security:spring-security-test")
```

---

## Testing Strategy

**Unit tests (new):**
- `AuthServiceTest` — mocks AuthProviderRegistry, UserRepository, RefreshTokenRepository
- `LocalAuthProviderTest` — validates password hashing/verification
- `GoogleAuthProviderTest` — validates JWKS fetching and JWT verification (uses WireMock)
- `JwtTokenServiceTest` — issue/validate/expired token scenarios

**Integration tests:**
- `AuthControllerIT` — extends `BaseIntegrationTest`, adds `@WireMockTest` to stub `GET /oauth2/v3/certs`. Tests:
  - Register with email+password → receive tokens → access protected endpoint
  - Login with correct / incorrect credentials
  - Google ID token validation → receive tokens
  - Refresh token rotation
  - Logout → refresh token revoked
  - Access protected endpoint with no token → 401
  - Access protected endpoint with expired token → 401
- `ListControllerIT` — updated to inject a valid JWT via `RequestSpecification` header

**WireMock setup:**  
`app.auth.google.jwks-uri` is overridden in test `application.yaml` to `http://localhost:${wiremock.server.port}/oauth2/v3/certs`. WireMock serves a locally-generated RSA key set; `GoogleAuthProvider` uses that key to verify tokens signed by the test helper.

---

## Security Checklist

- [ ] JWT secret externalized (environment variable / secrets manager in production)
- [ ] BCrypt strength 12 for passwords
- [ ] Refresh tokens stored as SHA-256 hash, never plaintext
- [ ] Single-use refresh tokens with rotation
- [ ] HTTPS enforced in production (not configured here — infrastructure concern)
- [ ] Google ID token validated: signature, audience (`aud` = our client ID), expiry
- [ ] No userId accepted from request body — always from JWT claims
- [ ] `user_shopping_list` ownership checked before any list mutation
- [ ] Rate limiting on auth endpoints (future work — not in this spec)
