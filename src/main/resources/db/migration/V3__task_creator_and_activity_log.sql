-- Persist creator display name on tasks (immutable after creation)
ALTER TABLE tasks ADD COLUMN created_by_name VARCHAR(255);

UPDATE tasks t
SET created_by_name = u.display_name
FROM users u
WHERE t.created_by = u.id;

ALTER TABLE tasks ALTER COLUMN created_by_name SET NOT NULL;

-- Activity history from workspace level downward
CREATE TABLE activity_logs (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspaces(id),
    scope_level   VARCHAR(32) NOT NULL,
    board_id      UUID REFERENCES boards(id),
    task_id       UUID REFERENCES tasks(id),
    actor_id      UUID NOT NULL REFERENCES users(id),
    actor_name    VARCHAR(255) NOT NULL,
    action        VARCHAR(64) NOT NULL,
    entity_type   VARCHAR(64) NOT NULL,
    entity_id     UUID,
    summary       TEXT NOT NULL,
    details       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_logs_workspace_created ON activity_logs (workspace_id, created_at DESC);
CREATE INDEX idx_activity_logs_board_created ON activity_logs (board_id, created_at DESC) WHERE board_id IS NOT NULL;
CREATE INDEX idx_activity_logs_task_created ON activity_logs (task_id, created_at DESC) WHERE task_id IS NOT NULL;
