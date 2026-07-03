ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);

ALTER TABLE product_images
    ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);
