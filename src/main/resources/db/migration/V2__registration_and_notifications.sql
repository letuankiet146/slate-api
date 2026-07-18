ALTER TABLE workspaces
    ADD COLUMN company_email VARCHAR(255) NULL;

CREATE UNIQUE INDEX idx_workspaces_company_email
    ON workspaces (LOWER(company_email))
    WHERE company_email IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE workspace_join_requests (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id   UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    company_email  VARCHAR(255) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    role_code      VARCHAR(64) NOT NULL DEFAULT 'BOARD_MEMBER',
    reviewed_by    UUID REFERENCES users(id),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_join_request_pending
    ON workspace_join_requests (user_id, workspace_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_join_requests_workspace_status
    ON workspace_join_requests (workspace_id, status);

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(64) NOT NULL,
    reference_id UUID NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id)
    WHERE read_at IS NULL;
