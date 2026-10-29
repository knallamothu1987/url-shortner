CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE urls (
    id UUID PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL UNIQUE,
    long_url VARCHAR(2048) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    click_count BIGINT NOT NULL DEFAULT 0,
    custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT short_code_format CHECK (short_code ~ '^[A-Za-z0-9_-]{4,20}$')
);

CREATE INDEX idx_urls_owner_created ON urls(owner_id, created_at DESC);
CREATE INDEX idx_urls_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;
