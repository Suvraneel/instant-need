-- Order number sequence: one per day, reset daily via application logic
-- Stored as a global counter; app generates WB-YYYYMMDD-NNNN format
CREATE SEQUENCE order_number_seq START 1;

CREATE TABLE orders (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number                VARCHAR(30)     NOT NULL UNIQUE,
    customer_id                 UUID            NOT NULL REFERENCES customers(id),
    shipping_address_snapshot   JSONB           NOT NULL,
    customer_snapshot           JSONB           NOT NULL,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    payment_method              VARCHAR(50)     NOT NULL DEFAULT 'cod',
    payment_note                TEXT,
    customer_note               TEXT,
    subtotal_amount             NUMERIC(14,2)   NOT NULL,
    total_amount                NUMERIC(14,2)   NOT NULL,
    currency_code               VARCHAR(3)      NOT NULL,
    placed_at                   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id  ON orders(customer_id);
CREATE INDEX idx_orders_status       ON orders(status);
CREATE INDEX idx_orders_placed_at    ON orders(placed_at DESC);
CREATE INDEX idx_orders_order_number ON orders(order_number);

CREATE TABLE order_items (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                    UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id                  UUID            REFERENCES products(id) ON DELETE SET NULL,
    product_name_snapshot       VARCHAR(255)    NOT NULL,
    sku_snapshot                VARCHAR(100)    NOT NULL,
    unit_of_measurement_snapshot VARCHAR(50)   NOT NULL,
    quantity                    INT             NOT NULL CHECK (quantity >= 1),
    unit_price                  NUMERIC(14,2)   NOT NULL,
    line_total                  NUMERIC(14,2)   NOT NULL,
    currency_code               VARCHAR(3)      NOT NULL,
    pricing_tier_snapshot       JSONB,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
