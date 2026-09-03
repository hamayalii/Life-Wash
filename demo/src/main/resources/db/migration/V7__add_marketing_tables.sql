-- Marketing Spend Table
-- Tracks monthly marketing/advertising expenses by category and campaign
CREATE TABLE marketing_spend (
    id BIGSERIAL PRIMARY KEY,
    period DATE NOT NULL,  -- First day of month (e.g., 2026-08-01)
    channel VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    campaign_name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_marketing_spend_period ON marketing_spend(period);
CREATE INDEX idx_marketing_spend_channel ON marketing_spend(channel);

-- Customer Value Table
-- Tracks cumulative customer lifetime value for CLV calculations
-- Updated via Spring Events (OrderSubmittedEvent) - NO database triggers
CREATE TABLE customer_value (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    customer_name VARCHAR(100) NOT NULL,
    total_lifetime_value DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    order_count INTEGER NOT NULL DEFAULT 0,
    first_order_date TIMESTAMP NOT NULL,
    last_order_date TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_value_phone ON customer_value(phone_number);
CREATE INDEX idx_customer_value_ltv ON customer_value(total_lifetime_value);
CREATE INDEX idx_customer_value_first_order ON customer_value(first_order_date);
