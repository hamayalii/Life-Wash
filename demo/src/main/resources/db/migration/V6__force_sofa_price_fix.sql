-- Force update SOFA base price to 40000.00 regardless of current value
-- This ensures existing databases are fixed even if V3/V5 were not applied
-- Formula: unitPrice = basePrice / sofaStandardSetSize
-- Example: 40000 / 10 = 4000 IQD per person
UPDATE service_pricing
SET base_price = 40000.00,
    sofa_standard_set_size = 10
WHERE service_type = 'SOFA';
