-- Fix NULL boolean values in service_pricing table
-- This prevents NullPointerException when Java unboxes Boolean to boolean

UPDATE service_pricing 
SET is_custom_priced = FALSE 
WHERE is_custom_priced IS NULL;

UPDATE service_pricing 
SET is_discount_active = FALSE 
WHERE is_discount_active IS NULL;

-- Optional: Add NOT NULL constraints to prevent future NULL values
-- Uncomment the following ALTER statements after verifying data integrity:

-- ALTER TABLE service_pricing 
-- ALTER COLUMN is_custom_priced SET NOT NULL;

-- ALTER TABLE service_pricing 
-- ALTER COLUMN is_discount_active SET NOT NULL;
