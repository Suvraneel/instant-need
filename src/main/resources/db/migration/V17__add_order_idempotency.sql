ALTER TABLE orders ADD COLUMN idempotency_key varchar(100);
ALTER TABLE orders ADD COLUMN idempotency_request_hash varchar(64);
CREATE UNIQUE INDEX uq_orders_customer_idempotency_key
    ON orders (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
