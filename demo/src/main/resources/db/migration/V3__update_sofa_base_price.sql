-- Update SOFA base price from 50000 to 40000 and ensure sofa_standard_set_size is 10
-- This migration fixes the pricing calculation for sofa services
-- Formula: unitPrice = basePrice / sofaStandardSetSize
-- Example: 40000 / 10 = 4000 IQD per person

UPDATE service_pricing
SET base_price = 40000.00,
    sofa_standard_set_size = 10
WHERE service_type = 'SOFA';
