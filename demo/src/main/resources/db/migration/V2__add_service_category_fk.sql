-- Add category_id column to services table for proper foreign key relationship
-- This migration fixes the architectural issue where Service.id was implicitly coupled to ServiceCategory.id

-- 1. Add the missing column
ALTER TABLE services ADD COLUMN category_id BIGINT;

-- 2. Migrate existing data (Legacy architecture coupled Service.id to ServiceCategory.id)
UPDATE services SET category_id = id WHERE category_id IS NULL;

-- 3. Add Foreign Key constraint for true referential integrity
ALTER TABLE services 
ADD CONSTRAINT fk_service_category 
FOREIGN KEY (category_id) 
REFERENCES service_categories (id);
