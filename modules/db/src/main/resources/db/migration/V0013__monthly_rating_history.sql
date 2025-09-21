-- Modify rating history to be month-based instead of timestamp-based
-- Drop the existing indexes first
DROP INDEX IF EXISTS rating_history_recorded_at_idx;
DROP INDEX IF EXISTS rating_history_player_recorded_idx;

-- Add month and year columns to track monthly ratings
ALTER TABLE rating_history 
ADD COLUMN month INTEGER,
ADD COLUMN year INTEGER;

-- Create a unique constraint to ensure only one record per player per month/year
ALTER TABLE rating_history 
ADD CONSTRAINT rating_history_player_month_year_unique 
UNIQUE (player_id, year, month);

-- Create new indexes for efficient month/year queries
CREATE INDEX rating_history_year_month_idx ON rating_history(year, month);
CREATE INDEX rating_history_player_year_month_idx ON rating_history(player_id, year, month);

-- Update existing records to extract month/year from recorded_at
-- This will help with any existing test data
UPDATE rating_history SET 
  year = EXTRACT(YEAR FROM recorded_at)::INTEGER,
  month = EXTRACT(MONTH FROM recorded_at)::INTEGER
WHERE year IS NULL OR month IS NULL;