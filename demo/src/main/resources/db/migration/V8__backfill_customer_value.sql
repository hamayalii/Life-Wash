-- Backfill customer_value table from existing orders
-- This migration aggregates historical order data to calculate initial customer lifetime values
-- NO database triggers - future updates will be handled by Spring Events

INSERT INTO customer_value (phone_number, customer_name, total_lifetime_value, order_count, first_order_date, last_order_date)
SELECT 
    phone_number,
    customer_name,
    COALESCE(SUM(grand_total), 0) as total_lifetime_value,
    COUNT(*) as order_count,
    MIN(created_at) as first_order_date,
    MAX(created_at) as last_order_date
FROM orders
WHERE phone_number IS NOT NULL 
  AND phone_number != ''
  AND work_status = 'ACCEPTED'
GROUP BY phone_number, customer_name
ON CONFLICT (phone_number) DO UPDATE SET
    total_lifetime_value = EXCLUDED.total_lifetime_value,
    order_count = EXCLUDED.order_count,
    last_order_date = EXCLUDED.last_order_date,
    updated_at = CURRENT_TIMESTAMP;
