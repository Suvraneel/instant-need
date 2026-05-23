-- V1: users, customers, addresses
-- gen_random_uuid() is built-in since PostgreSQL 13; no extension required.

CREATE TABLE users (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email                           VARCHAR(255)    UNIQUE,
    phone_number                    VARCHAR(20)     UNIQUE,
    password_hash                   VARCHAR(255)    NOT NULL,
    auth_provider                   VARCHAR(50)     NOT NULL DEFAULT 'LOCAL',
    role                            VARCHAR(50)     NOT NULL DEFAULT 'CUSTOMER',
    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    last_login_at                   TIMESTAMPTZ,
    password_reset_token            VARCHAR(255),
    password_reset_token_expires_at TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_email_or_phone CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);

CREATE INDEX idx_users_email        ON users (email);
CREATE INDEX idx_users_phone_number ON users (phone_number);

-- customers references addresses.id (nullable FK added after addresses is created)
CREATE TABLE customers (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    full_name                   VARCHAR(255) NOT NULL,
    business_name               VARCHAR(255),
    gst_vat_number              VARCHAR(100),
    notes                       TEXT,
    default_shipping_address_id UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_user_id ON customers (user_id);

CREATE TABLE addresses (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID        NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    label       VARCHAR(100) NOT NULL DEFAULT 'Default',
    line1       VARCHAR(255) NOT NULL,
    line2       VARCHAR(255),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_addresses_customer_id ON addresses (customer_id);

-- Deferred FK: customers.default_shipping_address_id -> addresses.id
ALTER TABLE customers
    ADD CONSTRAINT fk_customers_default_address
        FOREIGN KEY (default_shipping_address_id) REFERENCES addresses (id) ON DELETE SET NULL;
