-- V7: add inventory fields to products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS moq   INT NOT NULL DEFAULT 1
        CONSTRAINT chk_moq_positive CHECK (moq >= 1);

CREATE INDEX IF NOT EXISTS idx_products_stock ON products (stock);
