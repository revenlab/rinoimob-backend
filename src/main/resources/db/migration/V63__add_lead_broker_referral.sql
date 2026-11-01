ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS referred_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_leads_referred_by_user_id
    ON leads (referred_by_user_id)
    WHERE referred_by_user_id IS NOT NULL;
