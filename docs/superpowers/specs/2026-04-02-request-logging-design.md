# Request Logging Design

## Goal

Log each incoming HTTP request so developers can see what the API is receiving during development and operations.

## Approach

A single `OncePerRequestFilter` implementation placed in the REST adapter layer. Spring auto-detects it via `@Component` and applies it to every request.

## Component

**Class:** `RequestLoggingFilter`
**Package:** `com.listaai.infrastructure.adapter.input.rest`
**File:** `src/main/java/com/listaai/infrastructure/adapter/input/rest/RequestLoggingFilter.java`

Extends `OncePerRequestFilter`. Annotated with `@Component`.

## Behavior

On each request:
1. Extract `method` and `requestURI` from `HttpServletRequest`
2. Log at `INFO` level: `"{METHOD} {URI}"` — e.g., `POST /v1/lists`
3. Delegate to `filterChain.doFilter(request, response)`

## Log Format

```
INFO  c.l.i.a.i.r.RequestLoggingFilter - POST /v1/lists
INFO  c.l.i.a.i.r.RequestLoggingFilter - GET /v1/lists
INFO  c.l.i.a.i.r.RequestLoggingFilter - DELETE /v1/lists/1
INFO  c.l.i.a.i.r.RequestLoggingFilter - GET /v1/lists/1/items
```

## Scope

- No changes to existing controllers
- No changes to `application.yaml`
- One new file only
