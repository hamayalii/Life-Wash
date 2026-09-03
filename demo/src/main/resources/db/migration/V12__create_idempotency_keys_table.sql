CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(36) UNIQUE NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_data JSONB,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_key ON idempotency_keys(idempotency_key);
CREATE INDEX idx_expires_at ON idempotency_keys(expires_at);
