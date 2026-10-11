-- Add location fields to orders table
-- All fields nullable for backward compatibility
-- Use NUMERIC(10,6) for lat/lng (~11cm precision), DOUBLE for accuracy, TIMESTAMPTZ for timestamp
ALTER TABLE orders 
ADD COLUMN latitude NUMERIC(10,6);

ALTER TABLE orders 
ADD COLUMN longitude NUMERIC(10,6);

ALTER TABLE orders 
ADD COLUMN accuracy_meters DOUBLE PRECISION;

ALTER TABLE orders 
ADD COLUMN location_source VARCHAR(20);

ALTER TABLE orders 
ADD COLUMN location_captured_at TIMESTAMPTZ;

ALTER TABLE orders 
ADD COLUMN formatted_address TEXT;

-- Add index for location-based queries (future use)
CREATE INDEX idx_orders_location ON orders(latitude, longitude) 
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- Add index for location source queries
CREATE INDEX idx_orders_location_source ON orders(location_source) 
WHERE location_source IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN orders.latitude IS 'GPS latitude in decimal degrees (-90 to 90), NUMERIC(10,6) for ~11cm precision';
COMMENT ON COLUMN orders.longitude IS 'GPS longitude in decimal degrees (-180 to 180), NUMERIC(10,6) for ~11cm precision';
COMMENT ON COLUMN orders.accuracy_meters IS 'GPS accuracy in meters (0 to 10000)';
COMMENT ON COLUMN orders.location_source IS 'Source of location data: gps or manual';
COMMENT ON COLUMN orders.location_captured_at IS 'Timestamp when location was captured (timezone-aware)';
COMMENT ON COLUMN orders.formatted_address IS 'Reverse-geocoded address from Nominatim (informational only, not used in exports), capped at 500 chars';
