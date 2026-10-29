# 06 — API Specification

All APIs use JSON. Authentication via `Authorization: Bearer <jwt>` header.  
Base URL: `https://api.short.ly` (local: `http://localhost:8080`)

---

## 1. URL Shortener API

### POST /api/v1/urls — Create Short URL

**Auth:** Required (`ROLE_USER` or `ROLE_ADMIN`)

**Request:**
```json
{
  "longUrl": "https://www.example.com/very/long/path?with=params",
  "customAlias": "my-link",          // optional
  "expiresAt": "2027-01-01T00:00:00Z" // optional, ISO-8601
}
```

**Response 201 Created:**
```json
{
  "shortCode": "my-link",
  "shortUrl": "https://short.ly/my-link",
  "longUrl": "https://www.example.com/very/long/path?with=params",
  "expiresAt": "2027-01-01T00:00:00Z",
  "createdAt": "2026-10-28T12:00:00Z"
}
```

**Error Responses:**
- `400 Bad Request` — invalid URL format, alias already taken, alias contains banned chars
- `401 Unauthorized` — missing/invalid JWT
- `422 Unprocessable Entity` — URL blocked by safety check
- `429 Too Many Requests` — rate limit exceeded

---

### GET /{shortCode} — Redirect

**Auth:** None

**Response 302 Found:**
```
Location: https://www.example.com/very/long/path?with=params
```

**Error Responses:**
- `404 Not Found` — short code does not exist
- `410 Gone` — short code expired
- `429 Too Many Requests` — IP rate limit exceeded

---

### GET /api/v1/urls — List My URLs

**Auth:** Required

**Query Params:**
- `page` (default: 0), `size` (default: 20, max: 100)
- `sort` — `createdAt` (default) or `clickCount`
- `order` — `desc` (default) or `asc`

**Response 200 OK:**
```json
{
  "content": [
    {
      "shortCode": "abc123",
      "shortUrl": "https://short.ly/abc123",
      "longUrl": "https://...",
      "clickCount": 142,
      "createdAt": "2026-10-01T10:00:00Z",
      "expiresAt": null,
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

---

### GET /api/v1/urls/{shortCode} — Get URL Details

**Auth:** Required (owner or admin)

**Response 200 OK:**
```json
{
  "shortCode": "abc123",
  "shortUrl": "https://short.ly/abc123",
  "longUrl": "https://...",
  "clickCount": 142,
  "createdAt": "2026-10-01T10:00:00Z",
  "expiresAt": null,
  "active": true,
  "customAlias": false
}
```

---

### DELETE /api/v1/urls/{shortCode} — Delete URL

**Auth:** Required (owner or `ROLE_ADMIN`)

**Response 204 No Content**

---

### GET /api/v1/urls/{shortCode}/stats — Get Analytics

**Auth:** Required (owner or admin)

**Query Params:**
- `from` — ISO-8601 date (default: 30 days ago)
- `to` — ISO-8601 date (default: today)
- `granularity` — `hour`, `day` (default: `day`)

**Response 200 OK:**
```json
{
  "shortCode": "abc123",
  "totalClicks": 1420,
  "uniqueVisitors": 890,
  "timeSeries": [
    { "date": "2026-10-27", "clicks": 55, "unique": 38 },
    { "date": "2026-10-28", "clicks": 72, "unique": 51 }
  ],
  "topCountries": [
    { "code": "US", "name": "United States", "clicks": 640 },
    { "code": "GB", "name": "United Kingdom", "clicks": 210 }
  ],
  "topBrowsers": [
    { "browser": "Chrome", "clicks": 820 }
  ],
  "topDevices": [
    { "device": "MOBILE", "clicks": 950 },
    { "device": "DESKTOP", "clicks": 470 }
  ]
}
```

---

### PATCH /api/v1/admin/urls/{shortCode} — Admin Deactivate/Reactivate

**Auth:** Required (`ROLE_ADMIN`)

**Request:**
```json
{
  "active": false,
  "reason": "Reported as phishing"
}
```

**Response 200 OK:** Returns updated URL details.

---

## 2. Agentic Orchestration API

### POST /api/v1/pipelines — Start Pipeline Run

**Auth:** Required (`ROLE_AGENT_OPERATOR` or `ROLE_ADMIN`)

**Request:**
```json
{
  "name": "Add custom domain support",
  "scenarioType": "GREENFIELD",
  "requirement": "As a user, I want to use my own domain name for short URLs...",
  "context": {
    "targetService": "url-shortener-service",
    "priority": "HIGH"
  }
}
```

**Response 202 Accepted:**
```json
{
  "pipelineRunId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "name": "Add custom domain support",
  "createdAt": "2026-10-28T12:00:00Z",
  "statusUrl": "/api/v1/pipelines/550e8400-e29b-41d4-a716-446655440000"
}
```

---

### GET /api/v1/pipelines/{runId} — Get Pipeline Status

**Auth:** Required

**Response 200 OK:**
```json
{
  "pipelineRunId": "550e8400-...",
  "name": "Add custom domain support",
  "status": "RUNNING",
  "scenarioType": "GREENFIELD",
  "stages": [
    {
      "agentType": "REQUIREMENT",
      "taskName": "Normalize requirement spec",
      "status": "COMPLETED",
      "startedAt": "2026-10-28T12:00:05Z",
      "completedAt": "2026-10-28T12:00:42Z",
      "retryCount": 0
    },
    {
      "agentType": "DESIGN",
      "taskName": "Produce API and schema design",
      "status": "RUNNING",
      "startedAt": "2026-10-28T12:00:43Z",
      "completedAt": null,
      "retryCount": 0
    },
    {
      "agentType": "IMPLEMENTATION",
      "taskName": "Generate Java implementation",
      "status": "WAITING",
      "startedAt": null,
      "completedAt": null,
      "retryCount": 0
    }
  ],
  "pendingApprovals": [],
  "createdAt": "2026-10-28T12:00:00Z",
  "startedAt": "2026-10-28T12:00:05Z"
}
```

---

### POST /api/v1/pipelines/{runId}/approve — Submit Approval Decision

**Auth:** Required (`ROLE_AGENT_OPERATOR` or `ROLE_ADMIN`)

**Request:**
```json
{
  "approvalRequestId": "abc...",
  "decision": "APPROVED",
  "notes": "Schema change looks correct. Proceed."
}
```

**Response 200 OK:**
```json
{
  "approvalRequestId": "abc...",
  "decision": "APPROVED",
  "pipelineStatus": "RUNNING"
}
```

---

### POST /api/v1/pipelines/{runId}/cancel — Cancel Pipeline

**Auth:** Required

**Response 200 OK:**
```json
{
  "pipelineRunId": "550e8400-...",
  "status": "CANCELLED"
}
```

---

### GET /api/v1/pipelines/{runId}/context — Get Pipeline Context

**Auth:** Required (`ROLE_AGENT_OPERATOR` or `ROLE_ADMIN`)

Returns the full cross-agent context store for a pipeline run — useful for debugging and audit.

**Response 200 OK:**
```json
{
  "pipelineRunId": "550e8400-...",
  "context": {
    "requirementSpec": { ... },
    "designSpec": { ... },
    "decisionLog": [ ... ]
  }
}
```

---

### GET /api/v1/pipelines/{runId}/decisions — Get Decision Log

**Auth:** Required

**Response 200 OK:**
```json
{
  "pipelineRunId": "550e8400-...",
  "decisions": [
    {
      "id": "...",
      "decisionType": "AGENT_REASONING",
      "summary": "RequirementAgent identified 2 ambiguities and resolved both.",
      "details": { ... },
      "decidedBy": "RequirementAgent",
      "decidedAt": "2026-10-28T12:00:20Z"
    }
  ]
}
```

---

## 3. Health & Observability

### GET /actuator/health

Spring Boot Actuator standard endpoint.

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

### GET /actuator/metrics

Exposes Micrometer metrics in Prometheus format at `/actuator/prometheus`.

---

## 4. Error Response Format

All errors follow RFC 7807 Problem Details:

```json
{
  "type": "https://api.short.ly/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "URL creation limit of 100 per hour exceeded. Resets at 13:00 UTC.",
  "instance": "/api/v1/urls",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```
