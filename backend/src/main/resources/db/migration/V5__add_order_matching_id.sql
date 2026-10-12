ALTER TABLE orders
    ADD COLUMN matching_id UUID;

UPDATE orders
SET matching_id = gen_random_uuid()
WHERE matching_id IS NULL;

ALTER TABLE orders
    ALTER COLUMN matching_id SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT uq_orders_matching_id UNIQUE (matching_id);