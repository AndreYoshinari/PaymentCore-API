CREATE TABLE transfers (
    id                   UUID           PRIMARY KEY,
    sender_account_id    UUID           NOT NULL,
    recipient_account_id UUID           NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL,
    description          VARCHAR(255),
    status               VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED',
    idempotency_key      VARCHAR(100)   NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at          TIMESTAMPTZ,
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transfers_sender_account FOREIGN KEY (sender_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_recipient_account FOREIGN KEY (recipient_account_id) REFERENCES accounts (id),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (sender_account_id <> recipient_account_id),
    CONSTRAINT ck_transfers_status CHECK (status IN ('COMPLETED', 'REVERSED'))
);

CREATE TABLE account_transactions (
    id               UUID           PRIMARY KEY,
    account_id       UUID           NOT NULL,
    transfer_id      UUID           NOT NULL,
    transaction_type VARCHAR(30)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    description      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_account_transactions_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
    CONSTRAINT ck_account_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_account_transactions_type CHECK (transaction_type IN (
        'TRANSFER_SENT',
        'TRANSFER_RECEIVED',
        'REVERSAL_SENT',
        'REVERSAL_RECEIVED'
    ))
);

CREATE INDEX idx_transfers_sender_account_id_created_at
    ON transfers (sender_account_id, created_at);

CREATE INDEX idx_transfers_recipient_account_id_created_at
    ON transfers (recipient_account_id, created_at);

CREATE INDEX idx_account_transactions_account_id_created_at
    ON account_transactions (account_id, created_at);

CREATE INDEX idx_account_transactions_transfer_id
    ON account_transactions (transfer_id);
