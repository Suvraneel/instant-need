-- Cart: one active cart per customer
CREATE TABLE carts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID         NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CHECKED_OUT','ABANDONED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_carts_customer_active UNIQUE (customer_id)
);

CREATE INDEX idx_carts_customer_id ON carts(customer_id);

-- Cart items: one row per product in a cart
CREATE TABLE cart_items (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID           NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id          UUID           NOT NULL REFERENCES products(id),
    quantity            INT            NOT NULL CHECK (quantity >= 1),
    applied_unit_price  NUMERIC(14,2)  NOT NULL,
    line_total          NUMERIC(14,2)  NOT NULL,
    currency_code       VARCHAR(3)     NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
