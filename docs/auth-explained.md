# Authentication — What Was Built and Why

This document explains the authentication system added to Lista-AI. It assumes no prior knowledge of auth systems and tries to explain every decision from first principles.

---

## Why authentication at all?

Without authentication, any request to the API would work for any user. Someone could read your lists, delete them, or create new ones under your account. Authentication is the process of proving *who you are* before the server lets you do anything. Once the server knows who you are, it can apply *authorization* rules — in our case, only showing you the lists that belong to you.

---

## The database side: storing users

We added four new database tables.

### `users`

Stores one row per person. The key fields:

| Field | Why it exists |
|-------|---------------|
| `email` | The unique identifier for a user. Two accounts with the same email make no sense, so it has a `UNIQUE` constraint. |
| `name` | Display name. |
| `password_hash` | **Not** the password itself — a hashed version of it (explained below). Nullable because Google-login users never set a password. |
| `created_at` | Useful for debugging, support requests, etc. |

### `oauth_identities`

When someone logs in with Google, Google gives us a stable `sub` (subject) ID — a string like `"116832847392847563821"` — that uniquely identifies that person within Google. We store `(provider="google", provider_user_id="116832847...")` here, linked to the `users` row. This lets one user eventually have multiple login methods (Google today, GitHub tomorrow) all pointing to the same account.

### `refresh_tokens`

Stores one row per active refresh token session (explained in detail below). Only the SHA-256 hash of the token is stored, not the token itself.

### `user_shopping_list`

A join table connecting users to the lists they own. A simple `(user_id, list_id)` composite primary key with a `role` column (currently always `'owner'`). This design allows for future sharing — a list could have multiple users with different roles.

---

## Storing passwords safely: BCrypt

When a user registers with a password, we must not store it as plain text. If the database were ever compromised, the attacker would have everyone's passwords — which are likely reused on other sites too.

The solution is a **one-way hash function**: a mathematical function that is easy to compute in one direction but practically impossible to reverse. We use **BCrypt**, a standard designed specifically for passwords.

BCrypt has a **cost factor** (we use 12 in production). This controls how much CPU work is required to compute one hash. With cost 12, hashing a single password takes roughly 250–500ms — slow for an attacker running millions of guesses, but barely noticeable for a legitimate user logging in once.

When a user logs in, we:

1. Fetch their `password_hash` from the database.
2. Run BCrypt's verify function: it extracts the salt (a random value embedded in the hash) and re-hashes the provided password.
3. Compare the result to the stored hash. If they match, the password is correct.

We never "decrypt" the password — that is not possible by design.

> **Why cost 12?** OWASP's current recommendation for BCrypt is a minimum of 10. We chose 12 for a slightly better security margin. In tests, we use cost 4 to keep the test suite fast (BCrypt's cost is exponential: cost 4 is ~64× faster than cost 12).

---

## Proving identity on every request: JWT access tokens

HTTP is stateless — each request is independent. After logging in, the server needs a way to know who you are on the *next* request without asking you to log in again.

The classic approach is a **session**: the server stores a record of "user X is logged in" and gives the client a session ID cookie. The problem: every request must hit the database to look up the session. This is fine for small apps but becomes a bottleneck at scale.

We use a different approach: **JSON Web Tokens (JWT)**. A JWT is a small, self-contained token that the server issues to the client. The client sends it back on every request, and the server can verify it *without touching the database*.

### Structure of a JWT

A JWT has three parts separated by dots: `header.payload.signature`

**Header** — metadata about the token:
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload** — the claims (data inside the token):
```json
{
  "sub": "42",
  "iat": 1712345678,
  "exp": 1712346578
}
```

- `sub` (subject): the user's ID in our database.
- `iat` (issued at): Unix timestamp of when the token was created.
- `exp` (expiration): Unix timestamp after which the token is no longer valid.

**Signature** — a cryptographic signature over the header + payload, created using a secret key only the server knows.

When a request arrives with a JWT, the server:
1. Re-computes the signature using its secret key and the header + payload from the token.
2. Checks if the computed signature matches the one in the token. If not → reject (tampered token).
3. Checks if `exp` is in the future. If not → reject (expired token).
4. Reads `sub` to know which user this is. No database lookup needed.

> **Why HS256?** HS256 (HMAC-SHA-256) uses a single shared secret for both signing and verifying. It is simple, fast, and appropriate for a single-service API. The alternative, RS256, uses a private key to sign and a public key to verify — useful when multiple independent services need to verify tokens without sharing a secret, which is not our case.

### Lifetime: 15 minutes

Access tokens expire after 15 minutes. Short lifetimes limit the window of damage if a token is stolen — an attacker who intercepts one cannot use it for long.

---

## Staying logged in: refresh tokens

15 minutes is too short to stay usefully logged in. Refresh tokens solve this.

When you log in, the server issues *two* tokens:
- **Access token** — short-lived (15 min), used on every API request.
- **Refresh token** — long-lived (7 days), used *only* to get a new access token when the old one expires.

### Refresh token rotation

Each time you use a refresh token, the server:
1. Validates it.
2. Immediately revokes it (marks it as used).
3. Issues a *new* access token **and** a new refresh token.

This is called **token rotation**. The benefit: if an attacker steals a refresh token and uses it, the legitimate user's *next* refresh attempt will fail (their token was revoked). This signals a potential theft and the user can be asked to log in again.

### Why are refresh tokens stored in the database but access tokens are not?

Access tokens are verified entirely by their cryptographic signature — no database needed. Refresh tokens need to be *revocable* (for logout and rotation), which requires tracking state. We store only the **SHA-256 hash** of the refresh token, not the token itself. The actual token is a 32-byte random value (`SecureRandom`) sent to the client once. If the database were compromised, the attacker would have hashes — useless without the originals.

---

## Google OAuth2 sign-in

OAuth2 is a protocol that lets a third party (Google) vouch for a user's identity. When a user clicks "Sign in with Google":

1. The **client app** sends the user to Google's login page.
2. The user logs in to Google (in their browser).
3. Google redirects back to the client with an **ID token** — a JWT signed by Google's private key.
4. The client sends that ID token to *our* API (`POST /v1/auth/google`).
5. Our API validates the token and creates/finds the user.

### Validating the Google ID token

Google publishes its public keys at a well-known URL (`https://www.googleapis.com/oauth2/v3/certs`, the JWKS endpoint). We fetch these keys and use them to verify the token's RS256 signature. We also check:

- **Issuer**: must be `https://accounts.google.com`.
- **Audience**: must be our `GOOGLE_CLIENT_ID`. This prevents tokens issued to *other* apps from being replayed against our API.
- **Expiry**: the token must not be expired.

If all checks pass, we extract `sub` (the user's stable Google ID), `email`, and `name` from the token's claims, then find or create a user in our database.

> **Why not trust the email directly as the identifier?** Emails can change. The `sub` field is Google's permanent, immutable ID for that person. We store `sub` in `oauth_identities` and use it for lookups.

---

## Spring Security integration

Spring Security is the framework layer that sits between incoming HTTP requests and the rest of the application. It does two things:

1. **Filters requests** — for every incoming request, it checks whether it needs authentication and whether the provided credentials are valid.
2. **Injects identity** — once authenticated, the user's identity is available in controllers via `@AuthenticationPrincipal`.

### What we configured

**Public routes** — `/v1/auth/**` is open to everyone (obviously — you need to be able to log in before you have a token).

**Protected routes** — everything else requires a valid JWT in the `Authorization: Bearer <token>` header.

**JWT resource server** — Spring Security's built-in OAuth2 resource server support handles extracting and validating the JWT on every protected request. We provide it with our `JwtDecoder` (which knows the HS256 secret) and it does the rest.

**Stateless sessions** — we explicitly disable HTTP sessions. Every request is authenticated from scratch via the JWT. No cookies, no server-side session state.

**`AuthEntryPoint`** — when an unauthenticated request hits a protected route, Spring Security calls this class to write a 401 response. We use `ProblemDetail` (a standard RFC 9457 error format) serialized with Jackson so the response is always valid JSON, never a half-formed string.

---

## User ownership of lists

Before auth, `GET /v1/lists` returned every list in the database. That makes no sense once users exist.

The `user_shopping_list` join table connects users to their lists. When a user creates a list, a row is inserted into `user_shopping_list` linking their `user_id` to the new `list_id`. When they call `GET /v1/lists`, the query filters by `user_id` — they only see their own lists.

The controller extracts the user ID from the JWT's `sub` claim:

```java
@GetMapping
public ResponseEntity<List<ListResponse>> getAllLists(@AuthenticationPrincipal Jwt jwt) {
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(...listService.getAllLists(userId)...);
}
```

`@AuthenticationPrincipal Jwt jwt` is injected by Spring Security automatically — it is the validated JWT from the current request. No database lookup needed to find out who is asking.

---

## Testing strategy

Testing auth code correctly is tricky because it involves cryptography, external services (Google), and stateful tokens.

**Unit tests** cover individual components in isolation:
- `JwtTokenServiceTest` — verifies token generation, expiry, and hashing without a running server.
- `LocalAuthProviderTest` — verifies password checking against BCrypt hashes (using cost 4 for speed).
- `GoogleAuthProviderTest` — verifies Google token validation using **WireMock** to fake Google's JWKS endpoint. This means the test never calls the real Google servers.
- `AuthServiceTest` — verifies the orchestration logic (register, login, refresh, logout) with mocked dependencies.

**Integration tests** (`AuthControllerIT`) run the *entire* Spring Boot application against a real PostgreSQL database (via Testcontainers — a library that starts a Docker container for each test run). WireMock again fakes Google's JWKS endpoint. These tests exercise the full stack: HTTP → Spring Security → controller → service → database → response.

The integration tests cover every flow end-to-end:
- Register → 201 with tokens
- Duplicate email → 409
- Login → 200
- Wrong password → 401
- Google login → 200
- Refresh → new token pair
- Using a revoked (rotated) refresh token → 401
- Logout → subsequent refresh fails with 401
- No token on protected endpoint → 401
- Expired/invalid token → 401
