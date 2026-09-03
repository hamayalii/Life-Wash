CREATE TABLE service_pricing (
    id BIGSERIAL PRIMARY KEY,
    service_type VARCHAR(50) NOT NULL UNIQUE,
    pricing_unit VARCHAR(20) NOT NULL,
    base_price DECIMAL(10, 2) NOT NULL,
    discount_price DECIMAL(10, 2),
    discount_percentage DECIMAL(5, 2),
    discount_start_date TIMESTAMP,
    discount_end_date TIMESTAMP,
    is_discount_active BOOLEAN DEFAULT FALSE,
    sofa_standard_set_size INTEGER DEFAULT 10,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    service_type VARCHAR(50) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    locked_price DECIMAL(10, 2) NOT NULL,
    locked_discount DECIMAL(10, 2),
    locked_discount_percentage DECIMAL(5, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default pricing data
INSERT INTO service_pricing (service_type, pricing_unit, base_price, sofa_standard_set_size, created_at, updated_at) VALUES
('CARPET', 'PER_METER', 1250.00, NULL, NOW(), NOW()),
('RUG', 'PER_METER', 1250.00, NULL, NOW(), NOW()),
('BLANKET', 'PER_PIECE', 5000.00, NULL, NOW(), NOW()),
('CURTAINS', 'PER_METER', 1500.00, NULL, NOW(), NOW()),
('SOFA', 'PER_PERSON', 40000.00, 10, NOW(), NOW()),
('ROOF_TANK', 'PER_PIECE', 15000.00, NULL, NOW(), NOW());
