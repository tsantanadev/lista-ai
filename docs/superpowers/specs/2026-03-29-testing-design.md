# Test Suite Implementation Plan — Lista-AI

## Context

The project has no meaningful test coverage (only a context-load test). This plan adds a complete test suite: unit tests for the service layer and full-stack HTTP integration tests backed by a real PostgreSQL database via Testcontainers. JaCoCo enforces a 90% coverage minimum on the build. All tests are written in Java, matching the current source language.

---

## New Dependencies (`build.gradle.kts`)

Add to the `dependencies` block:
```kotlin
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
testImplementation("io.rest-assured:rest-assured")
```

Add `jacoco` plugin and configuration:
```kotlin
plugins {
    jacoco
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/ListaAiApplication*",
                    "**/domain/model/**",
                    "**/persistence/entity/**",
                    "**/mapper/**"  // generated MapStruct mappers
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            limit { minimum = "0.90".toBigDecimal() }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
```

Wire report generation before verification:
```kotlin
tasks.jacocoTestCoverageVerification { dependsOn(tasks.jacocoTestReport) }
```

---

## File Structure

```
src/test/java/com/listaai/
├── BaseIntegrationTest.java
├── application/service/
│   ├── ListServiceImplTest.java
│   └── ItemListServiceImplTest.java
└── infrastructure/adapter/input/rest/
    ├── ListControllerIT.java
    └── ItemListControllerIT.java
```

The existing `ListaAiApplicationTests.java` can be deleted or kept as-is (it becomes redundant).

---

## Critical Files

| File | Role |
|---|---|
| `build.gradle.kts` | Add dependencies + JaCoCo config |
| `src/main/java/com/listaai/application/service/ListServiceImpl.java` | Target of unit tests |
| `src/main/java/com/listaai/application/service/ItemListServiceImpl.java` | Target of unit tests |
| `src/main/java/com/listaai/infrastructure/adapter/input/rest/ListController.java` | Target of integration tests |
| `src/main/java/com/listaai/infrastructure/adapter/input/rest/ItemListController.java` | Target of integration tests |
| `src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase runs automatically in test context |

---

## Step 1 — Update `build.gradle.kts`

Add the four test dependencies and the full JaCoCo plugin block described above. Verify with `./gradlew dependencies --configuration testRuntimeClasspath` that the new deps resolve.

---

## Step 2 — `BaseIntegrationTest.java`

```
src/test/java/com/listaai/BaseIntegrationTest.java
```

- Annotated with `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Declares a `static PostgreSQLContainer<?> postgres` with `@Container` + `@ServiceConnection`
  - `@ServiceConnection` (from `spring-boot-testcontainers`) auto-configures the datasource — no `@DynamicPropertySource` needed
- `@BeforeAll` starts the container (JUnit 5 `@Testcontainers` handles lifecycle)
- `@BeforeEach` configures RestAssured `baseURI` and `port` from `@LocalServerPort`
- `@AfterEach` truncates `item_list` and `list` tables (in that order, respecting FK) via `JdbcTemplate`

---

## Step 3 — `ListServiceImplTest.java`

```
src/test/java/com/listaai/application/service/ListServiceImplTest.java
```

Plain JUnit 5 + Mockito. Uses `@ExtendWith(MockitoExtension.class)`.

| Test | Description |
|---|---|
| `getAll_returnsMappedLists` | `listRepository.findAll()` returns entities → service returns domain list |
| `getAll_returnsEmptyList` | repository returns empty → service returns empty list |
| `save_delegatesToRepository` | verifies `listRepository.save()` called with correct arg |
| `save_returnsPersistedDomain` | returned domain object matches what repository returns |
| `delete_delegatesToRepository` | verifies `listRepository.deleteById(id)` called |

---

## Step 4 — `ItemListServiceImplTest.java`

```
src/test/java/com/listaai/application/service/ItemListServiceImplTest.java
```

Same setup as above.

| Test | Description |
|---|---|
| `getItemsList_returnsMappedItems` | `itemListRepository.findAllByListId()` → mapped domain list |
| `getItemsList_returnsEmptyList` | empty repository response |
| `save_delegatesToRepository` | verifies save called with correct `CreateItemListCommand` data |
| `save_returnsPersistedDomain` | returned domain matches persisted entity |
| `update_delegatesToRepository` | verifies update called with correct `UpdateItemListCommand` |
| `update_returnsUpdatedDomain` | returned domain reflects updated fields |
| `delete_delegatesToRepository` | verifies `deleteByIdAndListId(itemId, listId)` called |

---

## Step 5 — `ListControllerIT.java`

```
src/test/java/com/listaai/infrastructure/adapter/input/rest/ListControllerIT.java
```

Extends `BaseIntegrationTest`. Uses RestAssured.

| Test | HTTP | Expected |
|---|---|---|
| `createList_returns201WithBody` | `POST /v1/lists` `{"name":"Groceries"}` | 201, body has `id` and `name` |
| `getAllLists_returnsSeededLists` | seed 2 lists, `GET /v1/lists` | 200, array size 2 |
| `getAllLists_returnsEmptyArray` | no seed, `GET /v1/lists` | 200, `[]` |
| `deleteList_returns204` | seed 1 list, `DELETE /v1/lists/{id}` | 204 |
| `deleteList_notFound_returns404` | `DELETE /v1/lists/9999` | 404 |

---

## Step 6 — `ItemListControllerIT.java`

```
src/test/java/com/listaai/infrastructure/adapter/input/rest/ItemListControllerIT.java
```

Extends `BaseIntegrationTest`. Each test seeds a parent list first (via RestAssured POST or `JdbcTemplate`).

| Test | HTTP | Expected |
|---|---|---|
| `getItems_returnsItemsForList` | seed list + 2 items, `GET /v1/lists/{listId}/items` | 200, 2 items |
| `getItems_emptyList` | seed list no items, `GET /v1/lists/{listId}/items` | 200, `[]` |
| `createItem_returns201` | `POST /v1/lists/{listId}/items` `{"description":"Milk"}` | 201, correct body |
| `updateItem_returns200` | seed item, `PUT /v1/lists/{listId}/items/{itemId}` toggle checked | 200, `checked: true` |
| `deleteItem_returns204` | seed item, `DELETE /v1/lists/{listId}/items/{itemId}` | 204 |

---

## Verification

```bash
# Run all tests
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Full check including coverage gate
./gradlew check

# View HTML report
open build/reports/jacoco/test/html/index.html
```

Coverage gate: build fails if overall instruction coverage (excluding mappers, entities, domain records, main class) falls below 90%.
