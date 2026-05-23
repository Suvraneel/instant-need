-- V2: categories, products, product_images, pricing_tiers

-- pg_trgm enables fast ILIKE / pattern-match searches on name and sku
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID         REFERENCES categories (id) ON DELETE SET NULL,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_slug      ON categories (slug);

CREATE TABLE products (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id           UUID          REFERENCES categories (id) ON DELETE SET NULL,
    name                  VARCHAR(255)  NOT NULL,
    slug                  VARCHAR(255)  NOT NULL UNIQUE,
    sku                   VARCHAR(100)  NOT NULL UNIQUE,
    description           TEXT,
    unit_of_measurement   VARCHAR(50),
    availability_status   VARCHAR(50)   NOT NULL DEFAULT 'IN_STOCK',
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    base_price            NUMERIC(12,2),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Trigram indexes for sub-second ILIKE search on name and sku (PRD NFR-3)
CREATE INDEX idx_products_name_trgm        ON products USING GIN (LOWER(name) gin_trgm_ops);
CREATE INDEX idx_products_sku_trgm         ON products USING GIN (LOWER(sku)  gin_trgm_ops);
CREATE INDEX idx_products_category_id      ON products (category_id);
CREATE INDEX idx_products_availability     ON products (availability_status);
CREATE INDEX idx_products_active           ON products (is_active);

CREATE TABLE product_images (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url   VARCHAR(500) NOT NULL,
    alt_text    VARCHAR(255),
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product_id ON product_images (product_id, sort_order);

CREATE TABLE pricing_tiers (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    min_quantity  INT           NOT NULL,
    max_quantity  INT,
    unit_price    NUMERIC(12,2) NOT NULL,
    currency_code VARCHAR(3)    NOT NULL DEFAULT 'INR',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tier_quantities CHECK (
        min_quantity >= 1 AND (max_quantity IS NULL OR max_quantity >= min_quantity)
    )
);

CREATE INDEX idx_pricing_tiers_product_id ON pricing_tiers (product_id, min_quantity);
