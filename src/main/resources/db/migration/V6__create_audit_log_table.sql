CREATE TABLE admin_audit_logs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id  UUID         NOT NULL REFERENCES users(id),
    action_type    VARCHAR(20)  NOT NULL CHECK (action_type IN ('CREATE','UPDATE','DELETE')),
    entity_type    VARCHAR(50)  NOT NULL,
    entity_id      VARCHAR(100) NOT NULL,
    description    TEXT,
    before_json    JSONB,
    after_json     JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_admin_user   ON admin_audit_logs(admin_user_id);
CREATE INDEX idx_audit_logs_entity       ON admin_audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at   ON admin_audit_logs(created_at DESC);
