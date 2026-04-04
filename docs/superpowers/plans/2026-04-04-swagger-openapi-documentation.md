# Swagger / OpenAPI Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add auto-generated OpenAPI/Swagger documentation to the Lista AI REST API with proper JWT security scheme and rich annotations on all 12 endpoints.

**Architecture:** Add `springdoc-openapi-starter-webmvc-ui` dependency, create an `OpenApiConfig` bean to declare API metadata and the JWT Bearer security scheme, permit Swagger UI paths in Spring Security, then annotate the three controllers (`AuthController`, `ListController`, `ItemListController`) with `@Tag`, `@Operation`, `@ApiResponse`, and `@SecurityRequirement`.

**Tech Stack:** springdoc-openapi 2.x (`org.springdoc:springdoc-openapi-starter-webmvc-ui`), Swagger/OpenAPI 3 annotations (`io.swagger.v3.oas.annotations.*`), Spring Boot 4.0.0, Spring Security OAuth2 Resource Server.

---

## Files to Create / Modify

| File | Action |
|------|--------|
| `build.gradle.kts` | Add springdoc-openapi dependency + Jacoco exclusion for `OpenApiConfig` |
| `src/main/java/com/listaai/infrastructure/config/OpenApiConfig.java` | **NEW** — API metadata + JWT Bearer security scheme bean |
| `src/main/java/com/listaai/infrastructure/security/SecurityConfig.java` | Permit Swagger UI and OpenAPI spec endpoints |
| `src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java` | Add `@Tag`, `@Operation`, `@ApiResponse` on 5 endpoints |
| `src/main/java/com/listaai/infrastructure/adapter/input/rest/ListController.java` | Add `@Tag`, `@SecurityRequirement`, `@Operation`, `@ApiResponse` on 3 endpoints |
| `src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java` | Add `@Tag`, `@SecurityRequirement`, `@Operation`, `@ApiResponse` on 4 endpoints |
| `src/test/java/com/listaai/infrastructure/adapter/input/rest/SwaggerIT.java` | **NEW** — integration test verifying `/v3/api-docs` is accessible |

---

## Task 1: Add springdoc-openapi Dependency

**Files:**
- Modify: `build.gradle.kts`

> **Note on version:** Spring Boot 4.0.0 uses Spring Framework 7. Check [Maven Central](https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui) for the latest version compatible with Spring Boot 4.0.0. Start with `2.8.4`; if the build fails with incompatibility errors, upgrade to the latest published version.

- [ ] **Step 1: Add the dependency to `build.gradle.kts`**

  In the `dependencies { }` block, after the existing `implementation` lines, add:

  ```kotlin
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")
  ```

- [ ] **Step 2: Also exclude OpenApiConfig from Jacoco coverage**

  In `tasks.jacocoTestReport`, add `"**/infrastructure/config/**"` to the `exclude(...)` list so the pure-wiring config class doesn't inflate coverage numbers.

  The updated exclude list in `build.gradle.kts` should include:

  ```kotlin
  "**/ListaAiApplication*",
  "**/domain/model/**",
  "**/persistence/entity/**",
  "**/adapter/input/rest/mapper/**",
  "**/adapter/output/persistence/mapper/**",
  "**/adapter/input/rest/dto/**",
  "**/port/input/**",
  "**/port/output/**",
  "**/persistence/repository/**",
  "**/infrastructure/config/**"
  ```

- [ ] **Step 3: Verify the dependency resolves and the project compiles**

  Run:
  ```bash
  ./gradlew build -x test
  ```
  Expected: `BUILD SUCCESSFUL` with no compilation errors.

- [ ] **Step 4: Commit**

  ```bash
  git add build.gradle.kts
  git commit -m "build: add springdoc-openapi dependency for Swagger UI"
  ```

---

## Task 2: Create OpenApiConfig and Secure Swagger UI Paths

**Files:**
- Create: `src/main/java/com/listaai/infrastructure/config/OpenApiConfig.java`
- Modify: `src/main/java/com/listaai/infrastructure/security/SecurityConfig.java`
- Create: `src/test/java/com/listaai/infrastructure/adapter/input/rest/SwaggerIT.java`

- [ ] **Step 1: Write the failing integration test first**

  Create `src/test/java/com/listaai/infrastructure/adapter/input/rest/SwaggerIT.java`:

  ```java
  package com.listaai.infrastructure.adapter.input.rest;

  import com.listaai.BaseIntegrationTest;
  import org.junit.jupiter.api.Test;

  import static io.restassured.RestAssured.given;
  import static org.hamcrest.Matchers.equalTo;
  import static org.hamcrest.Matchers.notNullValue;

  class SwaggerIT extends BaseIntegrationTest {

      @Test
      void openApiSpecIsAccessibleWithoutAuthentication() {
          given()
              .accept("application/json")
              .when()
              .get("/v3/api-docs")
              .then()
              .statusCode(200)
              .body("openapi", notNullValue())
              .body("info.title", equalTo("Lista AI API"));
      }

      @Test
      void swaggerUiIsAccessibleWithoutAuthentication() {
          given()
              .when()
              .get("/swagger-ui/index.html")
              .then()
              .statusCode(200);
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails (404 or 401 expected)**

  ```bash
  ./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.SwaggerIT"
  ```
  Expected: FAIL — `/v3/api-docs` returns 404 (springdoc not configured yet).

- [ ] **Step 3: Create `OpenApiConfig.java`**

  Create `src/main/java/com/listaai/infrastructure/config/OpenApiConfig.java`:

  ```java
  package com.listaai.infrastructure.config;

  import io.swagger.v3.oas.models.Components;
  import io.swagger.v3.oas.models.OpenAPI;
  import io.swagger.v3.oas.models.info.Info;
  import io.swagger.v3.oas.models.security.SecurityScheme;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;

  @Configuration
  public class OpenApiConfig {

      @Bean
      public OpenAPI openAPI() {
          return new OpenAPI()
              .info(new Info()
                  .title("Lista AI API")
                  .description("REST API for shopping list management. Protected endpoints require a Bearer JWT token obtained from /v1/auth/login or /v1/auth/register.")
                  .version("1.0.0"))
              .components(new Components()
                  .addSecuritySchemes("bearerAuth", new SecurityScheme()
                      .type(SecurityScheme.Type.HTTP)
                      .scheme("bearer")
                      .bearerFormat("JWT")
                      .description("JWT access token. Obtain from POST /v1/auth/login or POST /v1/auth/register, then pass as: Authorization: Bearer <token>")));
      }
  }
  ```

- [ ] **Step 4: Permit Swagger UI paths in `SecurityConfig.java`**

  In `src/main/java/com/listaai/infrastructure/security/SecurityConfig.java`, update the `authorizeHttpRequests` block to permit Swagger endpoints:

  ```java
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/v1/auth/**").permitAll()
      .requestMatchers("/actuator/health").permitAll()
      .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
      .anyRequest().authenticated()
  )
  ```

- [ ] **Step 5: Run tests to verify they pass**

  ```bash
  ./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.SwaggerIT"
  ```
  Expected: PASS — both tests green.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/listaai/infrastructure/config/OpenApiConfig.java \
          src/main/java/com/listaai/infrastructure/security/SecurityConfig.java \
          src/test/java/com/listaai/infrastructure/adapter/input/rest/SwaggerIT.java
  git commit -m "feat: add OpenAPI config and permit Swagger UI endpoints"
  ```

---

## Task 3: Annotate AuthController

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java`

- [ ] **Step 1: Replace `AuthController.java` with the annotated version**

  ```java
  package com.listaai.infrastructure.adapter.input.rest;

  import com.listaai.application.port.input.AuthUseCase;
  import com.listaai.infrastructure.adapter.input.rest.dto.*;
  import com.listaai.infrastructure.adapter.input.rest.mapper.AuthRestMapper;
  import io.swagger.v3.oas.annotations.Operation;
  import io.swagger.v3.oas.annotations.media.Content;
  import io.swagger.v3.oas.annotations.media.Schema;
  import io.swagger.v3.oas.annotations.responses.ApiResponse;
  import io.swagger.v3.oas.annotations.responses.ApiResponses;
  import io.swagger.v3.oas.annotations.tags.Tag;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  @RestController
  @RequestMapping("/v1/auth")
  @Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
  public class AuthController {

      private final AuthUseCase authUseCase;
      private final AuthRestMapper mapper;

      public AuthController(AuthUseCase authUseCase, AuthRestMapper mapper) {
          this.authUseCase = authUseCase;
          this.mapper = mapper;
      }

      @PostMapping("/register")
      @Operation(summary = "Register a new user",
                 description = "Creates a new user account and returns a JWT access token and refresh token.")
      @ApiResponses({
          @ApiResponse(responseCode = "201", description = "User registered successfully",
              content = @Content(schema = @Schema(implementation = TokenResponse.class))),
          @ApiResponse(responseCode = "409", description = "Email already registered",
              content = @Content)
      })
      public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
          return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(authUseCase.register(mapper.toCommand(request))));
      }

      @PostMapping("/login")
      @Operation(summary = "Login with email and password",
                 description = "Authenticates a user with local credentials and returns JWT tokens.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Login successful",
              content = @Content(schema = @Schema(implementation = TokenResponse.class))),
          @ApiResponse(responseCode = "401", description = "Invalid email or password",
              content = @Content)
      })
      public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
          return ResponseEntity.ok(mapper.toResponse(authUseCase.loginLocal(mapper.toCommand(request))));
      }

      @PostMapping("/google")
      @Operation(summary = "Login with Google",
                 description = "Authenticates a user using a Google ID token obtained from Google Sign-In. Creates the account if it does not exist.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Login successful",
              content = @Content(schema = @Schema(implementation = TokenResponse.class))),
          @ApiResponse(responseCode = "401", description = "Invalid or expired Google ID token",
              content = @Content)
      })
      public ResponseEntity<TokenResponse> google(@RequestBody GoogleAuthRequest request) {
          return ResponseEntity.ok(mapper.toResponse(authUseCase.loginGoogle(mapper.toCommand(request))));
      }

      @PostMapping("/refresh")
      @Operation(summary = "Refresh access token",
                 description = "Issues a new access token using a valid refresh token. The refresh token is rotated.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
              content = @Content(schema = @Schema(implementation = TokenResponse.class))),
          @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired",
              content = @Content)
      })
      public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
          return ResponseEntity.ok(mapper.toResponse(authUseCase.refresh(mapper.toCommand(request))));
      }

      @PostMapping("/logout")
      @Operation(summary = "Logout",
                 description = "Invalidates the provided refresh token. Subsequent refresh attempts with this token will fail.")
      @ApiResponses({
          @ApiResponse(responseCode = "204", description = "Logged out successfully"),
          @ApiResponse(responseCode = "401", description = "Refresh token is invalid",
              content = @Content)
      })
      public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
          authUseCase.logout(mapper.toCommand(request));
          return ResponseEntity.noContent().build();
      }
  }
  ```

- [ ] **Step 2: Run the existing auth integration tests to confirm nothing broke**

  ```bash
  ./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.AuthControllerIT"
  ```
  Expected: PASS.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/com/listaai/infrastructure/adapter/input/rest/AuthController.java
  git commit -m "docs: add OpenAPI annotations to AuthController"
  ```

---

## Task 4: Annotate ListController

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/ListController.java`

- [ ] **Step 1: Replace `ListController.java` with the annotated version**

  ```java
  package com.listaai.infrastructure.adapter.input.rest;

  import com.listaai.application.port.input.ListService;
  import com.listaai.application.port.input.command.CreateListCommand;
  import com.listaai.domain.model.ShoppingList;
  import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest;
  import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse;
  import com.listaai.infrastructure.adapter.input.rest.mapper.ListRestMapper;
  import io.swagger.v3.oas.annotations.Operation;
  import io.swagger.v3.oas.annotations.Parameter;
  import io.swagger.v3.oas.annotations.media.ArraySchema;
  import io.swagger.v3.oas.annotations.media.Content;
  import io.swagger.v3.oas.annotations.media.Schema;
  import io.swagger.v3.oas.annotations.responses.ApiResponse;
  import io.swagger.v3.oas.annotations.responses.ApiResponses;
  import io.swagger.v3.oas.annotations.security.SecurityRequirement;
  import io.swagger.v3.oas.annotations.tags.Tag;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.core.annotation.AuthenticationPrincipal;
  import org.springframework.security.oauth2.jwt.Jwt;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;

  @RestController
  @RequestMapping("/v1/lists")
  @Tag(name = "Shopping Lists", description = "Create, retrieve, and delete shopping lists")
  @SecurityRequirement(name = "bearerAuth")
  public class ListController {

      private final ListService listService;
      private final ListRestMapper mapper;

      public ListController(ListService listService, ListRestMapper mapper) {
          this.listService = listService;
          this.mapper = mapper;
      }

      @GetMapping
      @Operation(summary = "Get all shopping lists",
                 description = "Returns all shopping lists owned by the authenticated user.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Lists retrieved successfully",
              content = @Content(array = @ArraySchema(schema = @Schema(implementation = ListResponse.class)))),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<List<ListResponse>> getAllLists(@AuthenticationPrincipal Jwt jwt) {
          Long userId = Long.parseLong(jwt.getSubject());
          return ResponseEntity.ok(listService.getAllLists(userId).stream()
                  .map(mapper::toResponse).toList());
      }

      @PostMapping
      @Operation(summary = "Create a shopping list",
                 description = "Creates a new shopping list for the authenticated user.")
      @ApiResponses({
          @ApiResponse(responseCode = "201", description = "List created successfully",
              content = @Content(schema = @Schema(implementation = ListResponse.class))),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<ListResponse> createList(
              @RequestBody ListRequest request,
              @AuthenticationPrincipal Jwt jwt) {
          Long userId = Long.parseLong(jwt.getSubject());
          ShoppingList created = listService.createList(new CreateListCommand(request.name(), userId));
          return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
      }

      @DeleteMapping("/{id}")
      @Operation(summary = "Delete a shopping list",
                 description = "Deletes the shopping list with the given ID. Only the owner can delete their list.")
      @ApiResponses({
          @ApiResponse(responseCode = "204", description = "List deleted successfully"),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content),
          @ApiResponse(responseCode = "403", description = "List belongs to a different user",
              content = @Content)
      })
      public ResponseEntity<Void> deleteList(
              @Parameter(description = "ID of the shopping list to delete", required = true)
              @PathVariable Long id,
              @AuthenticationPrincipal Jwt jwt) {
          Long userId = Long.parseLong(jwt.getSubject());
          listService.deleteList(id, userId);
          return ResponseEntity.noContent().build();
      }
  }
  ```

- [ ] **Step 2: Run list integration tests**

  ```bash
  ./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ListControllerIT"
  ```
  Expected: PASS.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/com/listaai/infrastructure/adapter/input/rest/ListController.java
  git commit -m "docs: add OpenAPI annotations to ListController"
  ```

---

## Task 5: Annotate ItemListController

**Files:**
- Modify: `src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java`

- [ ] **Step 1: Replace `ItemListController.java` with the annotated version**

  ```java
  package com.listaai.infrastructure.adapter.input.rest;

  import com.listaai.application.port.input.ItemListService;
  import com.listaai.infrastructure.adapter.input.rest.dto.ItemListPostRequest;
  import com.listaai.infrastructure.adapter.input.rest.dto.ItemListResponse;
  import com.listaai.infrastructure.adapter.input.rest.dto.ItemListUpdateRequest;
  import com.listaai.infrastructure.adapter.input.rest.mapper.ItemListRestMapper;
  import io.swagger.v3.oas.annotations.Operation;
  import io.swagger.v3.oas.annotations.Parameter;
  import io.swagger.v3.oas.annotations.media.ArraySchema;
  import io.swagger.v3.oas.annotations.media.Content;
  import io.swagger.v3.oas.annotations.media.Schema;
  import io.swagger.v3.oas.annotations.responses.ApiResponse;
  import io.swagger.v3.oas.annotations.responses.ApiResponses;
  import io.swagger.v3.oas.annotations.security.SecurityRequirement;
  import io.swagger.v3.oas.annotations.tags.Tag;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;

  @RestController
  @RequestMapping("/v1/lists/{listId}/items")
  @Tag(name = "Shopping List Items", description = "Add, retrieve, update, and delete items within a shopping list")
  @SecurityRequirement(name = "bearerAuth")
  public class ItemListController {

      private final ItemListService service;
      private final ItemListRestMapper mapper;

      public ItemListController(ItemListService service, ItemListRestMapper mapper) {
          this.service = service;
          this.mapper = mapper;
      }

      @GetMapping
      @Operation(summary = "Get all items in a list",
                 description = "Returns all items belonging to the specified shopping list.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Items retrieved successfully",
              content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemListResponse.class)))),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<List<ItemListResponse>> getItemsList(
              @Parameter(description = "ID of the shopping list", required = true)
              @PathVariable long listId) {
          var result = service.getItemsList(listId).stream()
                  .map(mapper::toResponse)
                  .toList();
          return ResponseEntity.ok(result);
      }

      @PostMapping
      @Operation(summary = "Add an item to a list",
                 description = "Creates a new item in the specified shopping list. The item starts as unchecked.")
      @ApiResponses({
          @ApiResponse(responseCode = "201", description = "Item added successfully"),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<Void> postItemList(
              @RequestBody ItemListPostRequest request,
              @Parameter(description = "ID of the shopping list", required = true)
              @PathVariable long listId) {
          var command = mapper.toCreateCommand(request, listId);
          service.save(command);
          return ResponseEntity.status(HttpStatus.CREATED).build();
      }

      @PutMapping("/{itemId}")
      @Operation(summary = "Update an item",
                 description = "Updates the description and/or checked status of an existing item.")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Item updated successfully",
              content = @Content(schema = @Schema(implementation = ItemListResponse.class))),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<ItemListResponse> putItemList(
              @RequestBody ItemListUpdateRequest request,
              @Parameter(description = "ID of the shopping list", required = true)
              @PathVariable long listId,
              @Parameter(description = "ID of the item to update", required = true)
              @PathVariable long itemId) {
          var command = mapper.toUpdateCommand(request, itemId, listId);
          var result = service.update(command);
          return ResponseEntity.ok(mapper.toResponse(result));
      }

      @DeleteMapping("/{id}")
      @Operation(summary = "Delete an item",
                 description = "Removes an item from the shopping list.")
      @ApiResponses({
          @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
          @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
              content = @Content)
      })
      public ResponseEntity<Void> deleteItemList(
              @Parameter(description = "ID of the item to delete", required = true)
              @PathVariable long id,
              @Parameter(description = "ID of the shopping list", required = true)
              @PathVariable long listId) {
          service.delete(listId, id);
          return ResponseEntity.noContent().build();
      }
  }
  ```

- [ ] **Step 2: Run item list integration tests**

  ```bash
  ./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT"
  ```
  Expected: PASS.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java
  git commit -m "docs: add OpenAPI annotations to ItemListController"
  ```

---

## Task 6: Final Verification

- [ ] **Step 1: Run the full test suite including coverage check**

  ```bash
  ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, all tests pass, JaCoCo coverage ≥ 90%.

- [ ] **Step 2: Start the application and verify Swagger UI**

  ```bash
  docker-compose up -d && ./gradlew bootRun
  ```
  Then open a browser to `http://localhost:8080/swagger-ui/index.html`.

  Verify:
  - Three tag groups visible: **Authentication**, **Shopping Lists**, **Shopping List Items**
  - Each endpoint shows summary, description, and response codes
  - **Shopping Lists** and **Shopping List Items** endpoints show a lock icon (security requirement)
  - The "Authorize" button in the top-right accepts a Bearer JWT token
  - The OpenAPI spec JSON is accessible at `http://localhost:8080/v3/api-docs`

- [ ] **Step 3: Commit if any fixups needed, then finalize**

  If any annotation issues were found during manual verification, fix them now, re-run tests, and commit.

---

## Verification Summary

| Check | Command |
|-------|---------|
| Compile | `./gradlew build -x test` |
| Swagger test | `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.SwaggerIT"` |
| Auth tests | `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.AuthControllerIT"` |
| List tests | `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ListControllerIT"` |
| Item tests | `./gradlew test --tests "com.listaai.infrastructure.adapter.input.rest.ItemListControllerIT"` |
| Full suite + coverage | `./gradlew test` |
| Swagger UI (manual) | `http://localhost:8080/swagger-ui/index.html` |
