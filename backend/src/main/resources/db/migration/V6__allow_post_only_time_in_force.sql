ALTER TABLE orders
DROP CONSTRAINT chk_order_time_in_force;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_time_in_force
        CHECK (time_in_force IN (
                                 'GTC',
                                 'IOC',
                                 'FOK',
                                 'DAY',
                                 'POST_ONLY'
            ));