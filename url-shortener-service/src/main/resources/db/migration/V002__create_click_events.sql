CREATE TABLE click_events (
    id UUID PRIMARY KEY,
    url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    short_code VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    referrer VARCHAR(512),
    user_agent VARCHAR(512)
);

CREATE INDEX idx_click_events_url_time ON click_events(url_id, occurred_at DESC);
