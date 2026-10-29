# Agentic URL Shortener

This repository contains the URL Shortener domain service and the planned Agentic SDLC Orchestration service. The first implemented vertical slice is URL creation, owner-scoped URL listing/details/deletion, and public redirects backed by PostgreSQL and Redis.

## Requirements

- Java 21
- Maven 3.9+
- Docker Compose

## Run the URL service

1. Copy `.env.example` to `.env` and replace `JWT_SECRET` with a random value of at least 32 bytes. Set the values in your shell before starting the application; Spring Boot does not automatically load `.env`.
2. Start the local dependencies with `docker compose up -d postgres redis`.
3. From the repository root, start the service with `mvn -pl url-shortener-service spring-boot:run`. Flyway creates the schema on startup.
4. While the service is running, provision the UUID used as the JWT `sub` in PostgreSQL (the database's `users` table is owned by the upstream identity provider):

   ```sql
   INSERT INTO users (id, username, email)
   VALUES ('<JWT_SUBJECT_UUID>', 'local-user', 'local-user@example.invalid');
   ```

The service listens on `http://localhost:8080`. Flyway creates the initial schema on startup. Supply an HS256 bearer token whose `sub` is a UUID; an optional `roles` claim can contain `USER` or `ADMIN`. `GET /actuator/health` and the short-code redirect are public; URL management endpoints require a valid token.

On PowerShell, environment variables can be loaded for the current terminal with:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}
```

## Current API slice

- `POST /api/v1/urls` — create a short URL (supports an optional alias and expiry)
- `GET /api/v1/urls` — list the authenticated owner's URLs
- `GET /api/v1/urls/{shortCode}` — retrieve one of the owner's URLs
- `DELETE /api/v1/urls/{shortCode}` — delete one of the owner's URLs
- `GET /{shortCode}` — public HTTP 302 redirect; expired links return 410

Redis provides atomic Base62 sequence allocation and caches active redirects with expiry-aware TTLs. The orchestration service, analytics ingestion, rate limiting, and the remaining governance controls are subsequent implementation milestones described in [docs/11-implementation-tasks.md](docs/11-implementation-tasks.md).
