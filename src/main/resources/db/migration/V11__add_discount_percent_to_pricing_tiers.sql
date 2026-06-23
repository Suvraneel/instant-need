ALTER TABLE pricing_tiers ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2);
