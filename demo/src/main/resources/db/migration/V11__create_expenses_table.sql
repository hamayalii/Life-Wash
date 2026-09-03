-- Create expenses table for tracking operational costs
CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(15, 2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    period VARCHAR(7) NOT NULL,  -- Format: YYYY-MM (e.g., 2026-08)
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_expenses_period ON expenses(period);
CREATE INDEX idx_expenses_category ON expenses(category);
CREATE INDEX idx_expenses_period_category ON expenses(period, category);

-- Add constraint to ensure category is valid
ALTER TABLE expenses ADD CONSTRAINT chk_category_valid 
CHECK (category IN ('WAGES', 'RENT', 'DETERGENTS', 'CLEANING_EQUIPMENT', 
                    'FUEL', 'CAR_MAINTENANCE', 'UTILITIES', 
                    'FOOD_HOSPITALITY', 'TAXES_FEES', 'OTHER'));
