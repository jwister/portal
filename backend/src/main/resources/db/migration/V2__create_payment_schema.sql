CREATE TABLE payment_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(48) NOT NULL,
    newapi_user_id BIGINT NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    amount_usd_minor BIGINT NOT NULL,
    quota_to_credit BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    credited_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_orders_order_no UNIQUE (order_no)
);

CREATE INDEX idx_payment_orders_user_created ON payment_orders (newapi_user_id, created_at);
CREATE INDEX idx_payment_orders_status_expiry ON payment_orders (status, expires_at);

CREATE TABLE payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_order_id VARCHAR(128) NOT NULL,
    provider_capture_id VARCHAR(128) NULL,
    provider_status VARCHAR(64) NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT uk_payment_transactions_provider_capture UNIQUE (provider, provider_capture_id),
    CONSTRAINT uk_payment_transactions_order_provider UNIQUE (payment_order_id, provider)
);

CREATE TABLE payment_provider_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payment_order_id BIGINT NULL,
    verified_at DATETIME(6) NOT NULL,
    audit_summary VARCHAR(512) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_provider_events_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE credit_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_credit_attempts_order_created ON credit_attempts (payment_order_id, created_at);
