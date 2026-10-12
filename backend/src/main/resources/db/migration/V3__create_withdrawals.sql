
CREATE TABLE withdrawals (
                             id BIGSERIAL PRIMARY KEY,

                             user_id BIGINT NOT NULL
                                 REFERENCES users(id),

                             asset_id BIGINT NOT NULL
                                 REFERENCES assets(id),

                             amount NUMERIC(30, 10) NOT NULL
                                 CHECK (amount > 0),

                             idempotency_key VARCHAR(100) NOT NULL,

                             created_at TIMESTAMPTZ NOT NULL
                                 DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT uq_withdrawals_user_idempotency
                                 UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_withdrawals_user_created
    ON withdrawals (user_id, created_at DESC);
