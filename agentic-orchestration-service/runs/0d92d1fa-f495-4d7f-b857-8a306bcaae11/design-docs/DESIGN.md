# Design Documentation

## Components
- **ProvideCodeGenerationService** — Application service implementing: Provide a QR code generation endpoint returning HTTP 200 within 100 ms for each short link, available to 99 percent of requests
- **ProvideCodeGenerationController** — REST adapter exposing the new API

## API Changes
- `GET /api/v1/provide-code-generation` — New endpoint for: Provide a QR code generation endpoint returning HTTP 200 within 100 ms for each short link, available to 99 percent of requests

## Architecture Decision Records
- **ADR: Extend url-shortener-service in place for 'ProvideCodeGeneration'**: Implement inside the existing service following its layered api/application/domain structure — Matches existing conventions, avoids premature service extraction, and keeps the change reviewable
