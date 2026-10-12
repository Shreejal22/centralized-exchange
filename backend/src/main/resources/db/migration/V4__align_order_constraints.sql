
-- Keep legacy values readable while standardizing Java's CANCELLED value.
ALTER TABLE orders
    DROP CONSTRAINT chk_order_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status
    CHECK (status IN (
        'NEW',
        'PARTIALLY_FILLED',
        'FILLED',
        'CANCELED',
        'CANCELLED',
        'REJECTED',
        'EXPIRED'
    ));

-- MatchingEngine supports POST_ONLY.
ALTER TABLE orders
    DROP CONSTRAINT chk_order_time_in_force;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_time_in_force
    CHECK (time_in_force IN (
        'GTC',
        'IOC',
        'FOK',
        'POST_ONLY',
        'DAY'
    ));

-- Keep order history compatible with both cancellation spellings.
ALTER TABLE order_history
    DROP CONSTRAINT chk_order_history_to_status;

ALTER TABLE order_history
    ADD CONSTRAINT chk_order_history_to_status
    CHECK (to_status IN (
        'NEW',
        'PARTIALLY_FILLED',
        'FILLED',
        'CANCELED',
        'CANCELLED',
        'REJECTED',
        'EXPIRED'
    ));

ALTER TABLE order_history
    DROP CONSTRAINT chk_order_history_from_status;

ALTER TABLE order_history
    ADD CONSTRAINT chk_order_history_from_status
    CHECK (
        from_status IS NULL OR from_status IN (
            'NEW',
            'PARTIALLY_FILLED',
            'FILLED',
            'CANCELED',
            'CANCELLED',
            'REJECTED',
            'EXPIRED'
        )
    );
