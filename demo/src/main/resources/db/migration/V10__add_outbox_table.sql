-- Outbox Events Table
-- Implements Transactional Outbox Pattern for reliable event processing
-- Events are saved in the same transaction as the order creation
-- A scheduled processor polls and processes pending events
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- Index for efficient polling of pending events
CREATE INDEX idx_outbox_status ON outbox_events(status);

-- Index for time-based queries (cleanup, retry logic)
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);

-- Index for failed events that need retry
CREATE INDEX idx_outbox_status_retry ON outbox_events(status, retry_count);
