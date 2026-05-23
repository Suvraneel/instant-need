-- Replace the broad unique constraint (customer_id) with a partial unique index
-- that only enforces one ACTIVE cart per customer, allowing CHECKED_OUT/ABANDONED history.
ALTER TABLE carts DROP CONSTRAINT uq_carts_customer_active;

CREATE UNIQUE INDEX uq_carts_one_active_per_customer
    ON carts(customer_id)
    WHERE status = 'ACTIVE';
