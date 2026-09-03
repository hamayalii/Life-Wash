-- Add NOT NULL constraint to order_items.category_id
-- First, update any existing NULL values to point to a default category
UPDATE order_items 
SET category_id = (
    SELECT s.category_id 
    FROM services s 
    WHERE s.id = order_items.service_id
)
WHERE category_id IS NULL;

-- If still NULL (orphaned records), set to a default category ID
UPDATE order_items 
SET category_id = 1  -- Assuming category ID 1 exists as default
WHERE category_id IS NULL;

-- Add NOT NULL constraint
ALTER TABLE order_items 
ALTER COLUMN category_id SET NOT NULL;

-- Add foreign key constraint if not exists
ALTER TABLE order_items 
ADD CONSTRAINT fk_order_item_category 
FOREIGN KEY (category_id) 
REFERENCES service_categories(id);
