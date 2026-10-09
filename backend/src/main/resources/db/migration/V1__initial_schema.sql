-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,

                       username VARCHAR(50) NOT NULL UNIQUE,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,

                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- ASSETS
-- ============================================================

CREATE TABLE assets (
                        id BIGSERIAL PRIMARY KEY,

                        symbol VARCHAR(50) NOT NULL UNIQUE,
                        name VARCHAR(100) NOT NULL,

                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- TRADING PAIRS
-- ============================================================

CREATE TABLE trading_pairs (
                               id BIGSERIAL PRIMARY KEY,

                               symbol VARCHAR(50) NOT NULL UNIQUE,

                               base_asset_id BIGINT NOT NULL,
                               quote_asset_id BIGINT NOT NULL,

                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_trading_pair_base_asset
                                   FOREIGN KEY (base_asset_id)
                                       REFERENCES assets(id),

                               CONSTRAINT fk_trading_pair_quote_asset
                                   FOREIGN KEY (quote_asset_id)
                                       REFERENCES assets(id),

                               CONSTRAINT chk_different_assets
                                   CHECK (base_asset_id <> quote_asset_id)
);


-- ============================================================
-- WALLETS
-- ============================================================

CREATE TABLE wallets (
                         id BIGSERIAL PRIMARY KEY,

                         user_id BIGINT NOT NULL,

                         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_wallet_user
                             FOREIGN KEY (user_id)
                                 REFERENCES users(id),

                         CONSTRAINT uq_wallet_user
                             UNIQUE (user_id)
);


-- ============================================================
-- BALANCES
-- ============================================================

CREATE TABLE balances (
                          id BIGSERIAL PRIMARY KEY,

                          wallet_id BIGINT NOT NULL,
                          asset_id BIGINT NOT NULL,

                          available NUMERIC(30,10) NOT NULL DEFAULT 0,
                          reserved NUMERIC(30,10) NOT NULL DEFAULT 0,

                          version INTEGER NOT NULL DEFAULT 0,

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_balance_wallet
                              FOREIGN KEY (wallet_id)
                                  REFERENCES wallets(id),

                          CONSTRAINT fk_balance_asset
                              FOREIGN KEY (asset_id)
                                  REFERENCES assets(id),

                          CONSTRAINT uq_wallet_asset
                              UNIQUE (wallet_id, asset_id),

                          CONSTRAINT chk_available_non_negative
                              CHECK (available >= 0),

                          CONSTRAINT chk_reserved_non_negative
                              CHECK (reserved >= 0),

                          CONSTRAINT chk_balance_version_non_negative
                              CHECK (version >= 0)
);


-- ============================================================
-- LEDGER ACCOUNTS
-- ============================================================

CREATE TABLE accounts (
                          id BIGSERIAL PRIMARY KEY,

                          asset_id BIGINT NOT NULL,

                          name VARCHAR(100) NOT NULL,
                          account_type VARCHAR(30) NOT NULL,

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_account_asset
                              FOREIGN KEY (asset_id)
                                  REFERENCES assets(id),

                          CONSTRAINT uq_account_asset_name
                              UNIQUE (asset_id, name),

                          CONSTRAINT chk_account_type
                              CHECK (account_type IN (
                                                      'USER',
                                                      'SYSTEM',
                                                      'FEE',
                                                      'LIABILITY',
                                                      'EQUITY',
                                                      'REVENUE',
                                                      'EXPENSE',
                                                      'LIQUIDITY',
                                                      'INSURANCE'
                                  ))
);


-- ============================================================
-- LEDGER TRANSACTIONS
-- ============================================================

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,

                              transaction_type VARCHAR(30) NOT NULL,

                              reference_type VARCHAR(50),
                              reference_id BIGINT,

                              description VARCHAR(255),

                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT chk_transaction_type
                                  CHECK (transaction_type IN (
                                                              'DEPOSIT',
                                                              'WITHDRAWAL',
                                                              'TRADE',
                                                              'FEE',
                                                              'TRANSFER',
                                                              'ADJUSTMENT',
                                                              'REVERSAL'
                                      ))
);


-- ============================================================
-- LEDGER ENTRIES
-- ============================================================

CREATE TABLE ledger_entries (
                                id BIGSERIAL PRIMARY KEY,

                                transaction_id BIGINT NOT NULL,
                                account_id BIGINT NOT NULL,

                                entry_type VARCHAR(20) NOT NULL,
                                amount NUMERIC(30,10) NOT NULL,

                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_ledger_transaction
                                    FOREIGN KEY (transaction_id)
                                        REFERENCES transactions(id),

                                CONSTRAINT fk_ledger_account
                                    FOREIGN KEY (account_id)
                                        REFERENCES accounts(id),

                                CONSTRAINT chk_ledger_amount_positive
                                    CHECK (amount > 0),

                                CONSTRAINT chk_ledger_entry_type
                                    CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);


-- ============================================================
-- ORDERS
-- ============================================================

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,

                        user_id BIGINT NOT NULL,
                        trading_pair_id BIGINT NOT NULL,

                        side VARCHAR(10) NOT NULL,
                        order_type VARCHAR(20) NOT NULL,
                        time_in_force VARCHAR(20) NOT NULL,

                        price NUMERIC(30,10),
                        quantity NUMERIC(30,10) NOT NULL,
                        filled_quantity NUMERIC(30,10) NOT NULL DEFAULT 0,

                        status VARCHAR(20) NOT NULL,

                        client_order_id VARCHAR(100),

                        version INTEGER NOT NULL DEFAULT 0,

                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_order_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(id),

                        CONSTRAINT fk_order_trading_pair
                            FOREIGN KEY (trading_pair_id)
                                REFERENCES trading_pairs(id),

                        CONSTRAINT chk_order_side
                            CHECK (side IN ('BUY', 'SELL')),

                        CONSTRAINT chk_order_type
                            CHECK (order_type IN ('LIMIT', 'MARKET')),

                        CONSTRAINT chk_order_time_in_force
                            CHECK (time_in_force IN ('GTC', 'IOC', 'FOK', 'DAY')),

                        CONSTRAINT chk_order_status
                            CHECK (status IN (
                                              'NEW',
                                              'PARTIALLY_FILLED',
                                              'FILLED',
                                              'CANCELED',
                                              'REJECTED',
                                              'EXPIRED'
                                )),

                        CONSTRAINT chk_order_quantity_positive
                            CHECK (quantity > 0),

                        CONSTRAINT chk_order_filled_quantity_valid
                            CHECK (
                                filled_quantity >= 0
                                    AND filled_quantity <= quantity
                                ),

                        CONSTRAINT chk_order_price_positive
                            CHECK (price IS NULL OR price > 0),

                        CONSTRAINT chk_order_price_semantics
                            CHECK (
                                (order_type = 'MARKET' AND price IS NULL)
                                    OR
                                (order_type <> 'MARKET' AND price IS NOT NULL)
                                ),

                        CONSTRAINT chk_order_version_non_negative
                            CHECK (version >= 0),

                        CONSTRAINT uq_order_client_id
                            UNIQUE (user_id, client_order_id)
);


-- ============================================================
-- TRADES
-- ============================================================

CREATE TABLE trades (
                        id BIGSERIAL PRIMARY KEY,

                        trading_pair_id BIGINT NOT NULL,

                        maker_order_id BIGINT NOT NULL,
                        taker_order_id BIGINT NOT NULL,

                        price NUMERIC(30,10) NOT NULL,
                        quantity NUMERIC(30,10) NOT NULL,
                        quote_quantity NUMERIC(30,10) NOT NULL,

                        maker_fee_amount NUMERIC(30,10) NOT NULL DEFAULT 0,
                        maker_fee_asset_id BIGINT,
                        taker_fee_amount NUMERIC(30,10) NOT NULL DEFAULT 0,
                        taker_fee_asset_id BIGINT,

                        executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_trade_trading_pair
                            FOREIGN KEY (trading_pair_id)
                                REFERENCES trading_pairs(id),

                        CONSTRAINT fk_trade_maker_order
                            FOREIGN KEY (maker_order_id)
                                REFERENCES orders(id),

                        CONSTRAINT fk_trade_taker_order
                            FOREIGN KEY (taker_order_id)
                                REFERENCES orders(id),

                        CONSTRAINT fk_trade_maker_fee_asset
                            FOREIGN KEY (maker_fee_asset_id)
                                REFERENCES assets(id),

                        CONSTRAINT fk_trade_taker_fee_asset
                            FOREIGN KEY (taker_fee_asset_id)
                                REFERENCES assets(id),

                        CONSTRAINT chk_trade_price_positive
                            CHECK (price > 0),

                        CONSTRAINT chk_trade_quantity_positive
                            CHECK (quantity > 0),

                        CONSTRAINT chk_trade_quote_quantity_positive
                            CHECK (quote_quantity > 0),

                        CONSTRAINT chk_trade_quote_quantity_matches
                            CHECK (quote_quantity = price * quantity),

                        CONSTRAINT chk_trade_different_orders
                            CHECK (maker_order_id <> taker_order_id),

                        CONSTRAINT chk_trade_maker_fee_non_negative
                            CHECK (maker_fee_amount >= 0),

                        CONSTRAINT chk_trade_taker_fee_non_negative
                            CHECK (taker_fee_amount >= 0)
);


-- ============================================================
-- ORDER HISTORY
-- ============================================================

CREATE TABLE order_history (
                               id BIGSERIAL PRIMARY KEY,

                               order_id BIGINT NOT NULL,

                               from_status VARCHAR(20),
                               to_status VARCHAR(20) NOT NULL,

                               reason VARCHAR(255),

                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_order_history_order
                                   FOREIGN KEY (order_id)
                                       REFERENCES orders(id),

                               CONSTRAINT chk_order_history_to_status
                                   CHECK (to_status IN (
                                                        'NEW',
                                                        'PARTIALLY_FILLED',
                                                        'FILLED',
                                                        'CANCELED',
                                                        'REJECTED',
                                                        'EXPIRED'
                                       )),

                               CONSTRAINT chk_order_history_from_status
                                   CHECK (
                                       from_status IS NULL
                                           OR from_status IN (
                                                              'NEW',
                                                              'PARTIALLY_FILLED',
                                                              'FILLED',
                                                              'CANCELED',
                                                              'REJECTED',
                                                              'EXPIRED'
                                           )
                                       )
);


-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_log (
                           id BIGSERIAL PRIMARY KEY,

                           actor_type VARCHAR(30) NOT NULL,
                           actor_id BIGINT,

                           action VARCHAR(100) NOT NULL,

                           entity_type VARCHAR(50),
                           entity_id BIGINT,

                           before_state JSONB,
                           after_state JSONB,

                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT chk_audit_actor_type
                               CHECK (actor_type IN ('USER', 'ADMIN', 'SYSTEM'))
);


-- ============================================================
-- OUTBOX EVENTS
-- ============================================================

CREATE TABLE outbox_events (
                               id BIGSERIAL PRIMARY KEY,

                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id BIGINT NOT NULL,

                               event_type VARCHAR(100) NOT NULL,

                               payload JSONB NOT NULL,

                               processed_at TIMESTAMPTZ,

                               retry_count INTEGER NOT NULL DEFAULT 0,

                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT chk_outbox_retry_count
                                   CHECK (retry_count >= 0)
);


-- ============================================================
-- CANDLES / OHLCV MARKET DATA
-- ============================================================

CREATE TABLE candles (
                         id BIGSERIAL PRIMARY KEY,

                         trading_pair_id BIGINT NOT NULL,

                         interval VARCHAR(10) NOT NULL,

                         open_time TIMESTAMPTZ NOT NULL,

                         open NUMERIC(30,10) NOT NULL,
                         high NUMERIC(30,10) NOT NULL,
                         low NUMERIC(30,10) NOT NULL,
                         close NUMERIC(30,10) NOT NULL,

                         volume NUMERIC(30,10) NOT NULL DEFAULT 0,
                         quote_volume NUMERIC(30,10) NOT NULL DEFAULT 0,

                         trade_count INTEGER NOT NULL DEFAULT 0,

                         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_candle_trading_pair
                             FOREIGN KEY (trading_pair_id)
                                 REFERENCES trading_pairs(id),

                         CONSTRAINT uq_candle_pair_interval_time
                             UNIQUE (trading_pair_id, interval, open_time),

                         CONSTRAINT chk_candle_interval
                             CHECK (interval IN ('1m', '5m', '15m', '30m', '1h', '4h', '1d', '1w')),

    CONSTRAINT chk_candle_high_low
        CHECK (high >= low),

    CONSTRAINT chk_candle_prices_positive
        CHECK (open > 0 AND high > 0 AND low > 0 AND close > 0),

    CONSTRAINT chk_candle_volume
        CHECK (volume >= 0),

    CONSTRAINT chk_candle_quote_volume
        CHECK (quote_volume >= 0),

    CONSTRAINT chk_candle_trade_count
        CHECK (trade_count >= 0)
);


-- ============================================================
-- TRIGGER FUNCTIONS
-- ============================================================

-- Automatically maintain updated_at on UPDATE
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Enforce double-entry balance for each ledger transaction, per asset.
-- Runs at COMMIT because it is a deferred constraint trigger.
CREATE OR REPLACE FUNCTION check_ledger_balance()
RETURNS TRIGGER AS $$
DECLARE
v_transaction_id BIGINT;
    v_asset_id BIGINT;
    v_debits NUMERIC(30,10);
    v_credits NUMERIC(30,10);
BEGIN
    v_transaction_id := COALESCE(NEW.transaction_id, OLD.transaction_id);

FOR v_asset_id, v_debits, v_credits IN
SELECT a.asset_id,
       SUM(CASE WHEN le.entry_type = 'DEBIT'  THEN le.amount ELSE 0 END),
       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END)
FROM ledger_entries le
         JOIN accounts a ON a.id = le.account_id
WHERE le.transaction_id = v_transaction_id
GROUP BY a.asset_id
    LOOP
        IF v_debits <> v_credits THEN
            RAISE EXCEPTION
                'Unbalanced ledger transaction % for asset %: debits=%, credits=%',
                v_transaction_id, v_asset_id, v_debits, v_credits;
END IF;
END LOOP;

RETURN NULL;
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- TRIGGERS
-- ============================================================

-- updated_at triggers
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_balances_updated_at
    BEFORE UPDATE ON balances
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_candles_updated_at
    BEFORE UPDATE ON candles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


-- Deferred ledger balance enforcement
CREATE CONSTRAINT TRIGGER trg_ledger_balanced
    AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_ledger_balance();


-- ============================================================
-- INDEXES
-- ============================================================

-- Trading pairs
CREATE INDEX idx_trading_pairs_base_asset
    ON trading_pairs(base_asset_id);

CREATE INDEX idx_trading_pairs_quote_asset
    ON trading_pairs(quote_asset_id);


-- Balances
CREATE INDEX idx_balances_asset
    ON balances(asset_id);


-- Ledger
CREATE INDEX idx_ledger_entries_transaction_id
    ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_entries_account_id
    ON ledger_entries(account_id);


-- Transactions
CREATE INDEX idx_transactions_reference
    ON transactions(reference_type, reference_id);


-- Orders
-- Note: idx_orders_user_id removed — covered by uq_order_client_id (user_id, client_order_id)
CREATE INDEX idx_orders_trading_pair_id
    ON orders(trading_pair_id);

CREATE INDEX idx_orders_status
    ON orders(status);

CREATE INDEX idx_orders_pair_status
    ON orders(trading_pair_id, status);


-- Order history
CREATE INDEX idx_order_history_order_id
    ON order_history(order_id, created_at);


-- Trades
CREATE INDEX idx_trades_trading_pair
    ON trades(trading_pair_id);

CREATE INDEX idx_trades_maker_order
    ON trades(maker_order_id);

CREATE INDEX idx_trades_taker_order
    ON trades(taker_order_id);

CREATE INDEX idx_trades_executed_at
    ON trades(executed_at);

CREATE INDEX idx_trades_pair_executed_at
    ON trades(trading_pair_id, executed_at);


-- Outbox
CREATE INDEX idx_outbox_unprocessed
    ON outbox_events(created_at)
    WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);


-- Candles
-- Note: idx_candles_pair_time removed — covered by uq_candle_pair_interval_time


-- Audit log
CREATE INDEX idx_audit_log_entity
    ON audit_log(entity_type, entity_id, created_at);

CREATE INDEX idx_audit_log_actor
    ON audit_log(actor_type, actor_id, created_at);