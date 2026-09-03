-- Add rejection_reason column to orders table
-- This column will store the enum value for why an order was rejected
-- NULL values are acceptable for historical orders rejected before this feature
ALTER TABLE orders 
ADD COLUMN rejection_reason VARCHAR(50);

-- Add order_source column to orders table
-- This column tracks where the order originated (WEB, POS, TELEGRAM_BOT)
-- Replaces fragile magic strings in createdBy field
ALTER TABLE orders 
ADD COLUMN order_source VARCHAR(50);

-- Backfill order_source for existing orders based on createdBy field
-- This ensures existing data is migrated to the new enum-based system
UPDATE orders 
SET order_source = CASE 
    WHEN created_by = 'WEBSITE' THEN 'WEB'
    WHEN created_by IN ('pos_operator', 'admin') THEN 'POS'
    WHEN created_by = 'TELEGRAM_BOT' THEN 'TELEGRAM_BOT'
    ELSE 'WEB'  -- Default fallback for unknown sources
END
WHERE order_source IS NULL;

-- Add index for performance on rejection queries
CREATE INDEX idx_orders_rejection_reason ON orders(rejection_reason);

-- Add index for composite query (status + reason) used by chart API
CREATE INDEX idx_orders_status_reason ON orders(work_status, rejection_reason);

-- Add index for order_source queries (POS history, etc.)
CREATE INDEX idx_orders_order_source ON orders(order_source);

-- Add composite index for POS orders (source + status)
CREATE INDEX idx_orders_source_status ON orders(order_source, work_status);
