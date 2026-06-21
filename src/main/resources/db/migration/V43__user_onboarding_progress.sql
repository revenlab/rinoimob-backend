CREATE TABLE user_onboarding_progress (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    tutorial_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_step_key VARCHAR(120),
    last_route VARCHAR(255),
    started_at TIMESTAMP,
    dismissed_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_onboarding_progress UNIQUE (tenant_id, user_id, tutorial_key),
    CONSTRAINT fk_user_onboarding_progress_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_onboarding_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_onboarding_progress_lookup
    ON user_onboarding_progress (tenant_id, user_id);
