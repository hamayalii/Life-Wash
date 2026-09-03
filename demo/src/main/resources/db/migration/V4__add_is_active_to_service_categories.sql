-- Add is_active column to service_categories table for soft delete functionality
-- This enables services to be marked as inactive without breaking referential integrity

-- 1. Add the is_active column with default value true for existing records
ALTER TABLE service_categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Create index on is_active for better query performance on active services
CREATE INDEX idx_service_categories_is_active ON service_categories(is_active);
