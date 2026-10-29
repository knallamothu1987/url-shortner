# Design Documentation

## Components
- **TrackAnalyticsRedirectService** — Application service implementing: Track analytics for every redirect and store click events in a table, exposing the top 5 referrers per short link
- **TrackAnalyticsRedirectController** — REST adapter exposing the new API
- **TrackAnalyticsRedirectRepository** — JPA persistence for new data

## API Changes
- `GET /api/v1/track-analytics-redirect` — New endpoint for: Track analytics for every redirect and store click events in a table, exposing the top 5 referrers per short link

## Architecture Decision Records
- **ADR: Extend url-shortener-service in place for 'TrackAnalyticsRedirect'**: Implement inside the existing service following its layered api/application/domain structure — Matches existing conventions, avoids premature service extraction, and keeps the change reviewable
