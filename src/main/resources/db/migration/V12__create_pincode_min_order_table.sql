CREATE TABLE pincode_min_orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pincode     VARCHAR(10)    NOT NULL UNIQUE,
    min_amount  NUMERIC(12, 2) NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
