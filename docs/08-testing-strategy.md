# 08 — Testing Strategy

## 1. Testing Pyramid

```
           ┌──────────────────────┐
           │  Contract / E2E Tests │  (few, slow — verify system behavior)
           ├──────────────────────┤
           │  Integration Tests    │  (moderate — Testcontainers, real DB/cache)
           ├──────────────────────┤
           │  Unit Tests           │  (many, fast — pure domain logic)
           └──────────────────────┘
```

Target coverage: ≥ 80% line coverage across all modules (enforced via JaCoCo in CI).

---

## 2. Unit Tests

**Framework:** JUnit 5 + Mockito + AssertJ

**What gets unit tested:**
- `ShortCodeGenerator` — collision rate, character set compliance, configurable length
- `UrlValidator` — valid/invalid URLs, protocol check, malformed inputs
- `UrlShorteningService` — business logic (duplicate check, expiry setting, custom alias logic)
- `RedirectService` — cache-hit path, cache-miss path, expired URL handling
- `AnalyticsService` — aggregation logic, time-series bucketing
- `GateEvaluatorService` — each gate type (SchemaGate, BuildGate, PolicyGate)
- `OrchestratorService` — DAG traversal, retry logic, parallel task launch
- Each `Agent` class — verify correct prompts sent, output parsed correctly (using mock `ChatClient`)

**Agent unit test pattern:**
```java
@Test
void requirementAgent_shouldResolveAmbiguity_whenInputIsVague() {
    // Given
    String vagueRequirement = "Make it faster";
    ChatClient mockClient = mock(ChatClient.class);
    // Stub LLM response with a structured RequirementSpec JSON
    when(mockClient.prompt(any()).call().entity(RequirementSpec.class))
        .thenReturn(buildExpectedSpec());
    
    RequirementAgent agent = new RequirementAgent(mockClient);
    
    // When
    RequirementSpec spec = agent.process(vagueRequirement, context);
    
    // Then
    assertThat(spec.getAmbiguities()).isNotEmpty();
    assertThat(spec.getStatus()).isEqualTo(SpecStatus.INFERRED);
}
```

---

## 3. Integration Tests

**Framework:** JUnit 5 + Testcontainers + Spring Boot Test + WireMock

**Infrastructure containers used:**
- `PostgreSQLContainer` — real database with Flyway migrations applied
- `GenericContainer("redis:7-alpine")` — real Redis
- `KafkaContainer` — real Kafka broker (Confluent CP)
- `WireMockServer` — mock external HTTP APIs (blocklist, GeoIP)

**Key integration test scenarios:**

### 3.1 URL Shortener Service

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class UrlShorteningIT {

    @Container static PostgreSQLContainer<?> postgres = ...;
    @Container static GenericContainer<?> redis = ...;

    @Test
    void createAndRedirect_roundTrip() {
        // Create short URL
        // Assert 201 + shortCode returned
        // Hit /{shortCode}
        // Assert 302 with correct Location header
        // Assert click_events row inserted
    }

    @Test
    void redirect_returnsGone_whenExpired() { ... }

    @Test
    void redirect_servesFromCache_whenDbDown() {
        // Pre-warm Redis cache
        // Stop PostgreSQL container
        // Hit /{shortCode}
        // Assert 302 from cache (circuit breaker)
    }
}
```

### 3.2 Analytics Pipeline

```java
@Test
void clickEvent_isProcessed_asynchronously() {
    // Publish click event to Kafka
    // Await (Awaitility, max 10s)
    // Assert click_events row in DB
    // Assert Redis counter incremented
}
```

### 3.3 Agentic Pipeline

```java
@SpringBootTest
@Testcontainers
class OrchestratorIT {

    @Test
    void greenfieldPipeline_completesSuccessfully() {
        // Start pipeline run with simple requirement
        // Poll status endpoint
        // Await COMPLETED status (Awaitility, max 60s with mock LLM)
        // Assert all stages COMPLETED
        // Assert output artifacts present in context store
    }

    @Test
    void pipeline_pauses_atHumanApprovalGate() {
        // Start pipeline that hits an approval gate
        // Assert status = PAUSED, approvalRequest present
        // Submit approval decision
        // Assert pipeline resumes and completes
    }

    @Test
    void pipeline_retries_onAgentFailure_thenSucceeds() {
        // Configure mock LLM to fail twice then succeed
        // Assert retry_count = 2 on agent task
        // Assert pipeline completes COMPLETED (not FAILED)
    }

    @Test
    void pipeline_rollsBack_afterMaxRetriesExceeded() {
        // Configure mock LLM to always fail
        // Assert retry_count = 3
        // Assert stage status = FAILED
        // Assert pipeline status = FAILED
        // Assert rollback actions executed
    }
}
```

---

## 4. Contract Tests

**Framework:** Spring Cloud Contract or Pact

Used between:
- `Spring Cloud Gateway` ↔ `URL Shortener Service`
- `URL Shortener Service` ↔ `Analytics Consumer` (Kafka contracts)

Ensures interface stability when services evolve independently.

---

## 5. Performance Tests

**Framework:** Gatling (JVM-native load testing)

**Scenarios:**
```scala
// Redirect throughput test
scenario("Redirect Load")
  .exec(http("Redirect").get("/abc123"))
  
setUp(
  scenario.inject(
    rampUsersPerSec(100).to(1000).during(60.seconds),
    constantUsersPerSec(1000).during(120.seconds)
  )
).assertions(
  global.responseTime.percentile(99).lt(50),  // p99 < 50ms
  global.successfulRequests.percent.gt(99.9)
)
```

**Also covers:**
- URL creation throughput
- Analytics query under load
- Redis eviction behavior at high traffic

---

## 6. Chaos / Resilience Tests

Applied in Scenario 3 (Ambiguous). Framework: Testcontainers + Awaitility.

| Test | Method | Expected Outcome |
|------|--------|-----------------|
| DB outage during redirect | Stop Postgres container | 302 served from Redis cache |
| Redis outage during redirect | Stop Redis container | 302 served from DB (slower but functional) |
| Circuit breaker open | Inject DB errors at rate > threshold | Circuit opens, fallback invoked |
| Kafka broker down | Stop Kafka container | URL creation succeeds; analytics event queued in retry buffer |
| LLM API timeout | WireMock: delay > 30s | Agent retries with exponential backoff; falls back after max retries |

---

## 7. Security Tests

**OWASP ZAP** (automated DAST) — run against local Docker environment in CI.

**Checklist:**
- SQL injection on all query params
- XSS in URL input
- JWT tampering (invalid signature, expired, wrong algorithm)
- IDOR: accessing another user's URL analytics
- Rate limit bypass attempts
- Open redirect via manipulated long URL

---

## 8. Test Data Management

- **Flyway test fixtures**: `src/test/resources/db/testdata/` — applied after schema migrations in test profile
- **Test factories**: `UrlTestFactory`, `PipelineRunTestFactory` — builder pattern for clean test data setup
- **No shared state between tests**: Each test runs in a transaction rolled back on completion (or uses container isolation)

---

## 9. CI Pipeline Test Execution

```
Pull Request:
  → mvn test                          (unit tests, fast — < 2 min)
  → mvn verify -P integration-tests   (integration tests — < 10 min)
  → OWASP Dependency Check
  → SpotBugs + PMD

Merge to main:
  → All above
  → Gatling performance smoke test    (5-minute load test)
  → OWASP ZAP security scan
  → Docker image build + push
```
