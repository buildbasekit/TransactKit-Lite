CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    business_reference VARCHAR(100) NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255),
    stripe_checkout_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    checkout_attempt_reference VARCHAR(100),
    checkout_status VARCHAR(32),
    amount BIGINT NOT NULL,
    refunded_amount BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE refunds (
    id VARCHAR(36) PRIMARY KEY,
    stripe_refund_id VARCHAR(255) NOT NULL UNIQUE,
    payment_id VARCHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE TABLE webhook_events (
    id VARCHAR(36) PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    error_message VARCHAR(1000)
);
