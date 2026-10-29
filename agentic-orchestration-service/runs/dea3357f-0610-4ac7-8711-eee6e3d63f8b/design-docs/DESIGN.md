# Design Documentation

## Components
- **BuildUrlShortenerService** — Application service implementing: Build a URL shortener with custom aliases
- **BuildUrlShortenerController** — REST adapter exposing the new API

## API Changes
- `GET /api/v1/build-url-shortener` — New endpoint for: Build a URL shortener with custom aliases

## Architecture Decision Records
- **ADR: Extend url-shortener-service in place for 'BuildUrlShortener'**: Implement inside the existing service following its layered api/application/domain structure — Matches existing conventions, avoids premature service extraction, and keeps the change reviewable
