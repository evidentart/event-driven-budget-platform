CREATE SEQUENCE IF NOT EXISTS expense_outbox_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS expense_outbox (
    outbox_sequence BIGINT PRIMARY KEY DEFAULT nextval('expense_outbox_sequence'),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_id UUID NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    locked_until TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_expense_outbox_status_sequence
    ON expense_outbox (status, outbox_sequence);
