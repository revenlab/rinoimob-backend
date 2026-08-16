ALTER TABLE verification_tokens
    ADD COLUMN IF NOT EXISTS pending_email VARCHAR(255);
