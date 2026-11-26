ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_user_id UNIQUE (user_id);

DROP INDEX idx_accounts_user_id;
