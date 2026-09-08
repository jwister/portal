ALTER TABLE payment_orders
    ADD COLUMN payment_address_id BIGINT NULL;
ALTER TABLE payment_orders
    ADD COLUMN receive_address VARCHAR(64) NULL;
ALTER TABLE payment_orders
    ADD COLUMN payable_minor BIGINT NULL;
ALTER TABLE payment_orders
    ADD COLUMN payable_currency VARCHAR(8) NULL;
ALTER TABLE payment_orders
    ADD COLUMN payable_scale INT NULL;
ALTER TABLE payment_orders
    ADD COLUMN submitted_txid VARCHAR(128) NULL;
ALTER TABLE payment_orders
    ADD COLUMN txid_check_count INT NOT NULL DEFAULT 0;
ALTER TABLE payment_orders
    ADD COLUMN last_txid_checked_at DATETIME(3) NULL;
ALTER TABLE payment_orders
    ADD COLUMN next_txid_check_at DATETIME(3) NULL;
ALTER TABLE payment_orders
    ADD COLUMN last_txid_check_result VARCHAR(64) NULL;

CREATE TABLE payment_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(64) NOT NULL,
    active_order_count INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_addresses_address UNIQUE (address)
);

CREATE TABLE payment_amount_registries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_address_id BIGINT NOT NULL,
    payable_minor BIGINT NOT NULL,
    payment_order_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_amount_address_amount UNIQUE (payment_address_id, payable_minor),
    CONSTRAINT uk_payment_amount_order UNIQUE (payment_order_id),
    CONSTRAINT fk_payment_amount_address FOREIGN KEY (payment_address_id) REFERENCES payment_addresses(id),
    CONSTRAINT fk_payment_amount_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders(id)
);

CREATE TABLE chain_transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    txid VARCHAR(128) NOT NULL,
    log_index BIGINT NOT NULL,
    contract_address VARCHAR(64) NOT NULL,
    from_address VARCHAR(64) NOT NULL,
    to_address VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    block_number BIGINT NOT NULL,
    block_time DATETIME(3) NOT NULL,
    payment_order_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chain_transfer_tx_log UNIQUE (txid, log_index),
    CONSTRAINT fk_chain_transfer_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders(id)
);

CREATE TABLE payment_scan_cursors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL,
    payment_address_id BIGINT NOT NULL,
    fingerprint VARCHAR(512) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_scan_provider_address UNIQUE (provider, payment_address_id),
    CONSTRAINT fk_payment_scan_address FOREIGN KEY (payment_address_id) REFERENCES payment_addresses(id)
);

ALTER TABLE payment_orders ADD CONSTRAINT fk_payment_orders_address
    FOREIGN KEY (payment_address_id) REFERENCES payment_addresses(id);
CREATE INDEX idx_payment_orders_address_amount ON payment_orders (payment_address_id, payable_minor);
CREATE INDEX idx_payment_orders_txid_retry ON payment_orders (status, next_txid_check_at);
