ALTER TABLE automation_executions
    ADD COLUMN IF NOT EXISTS resume_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_automation_executions_resume_at
    ON automation_executions(resume_at);
