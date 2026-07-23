-- Support permanent user/workspace removal while keeping denormalized audit names.
ALTER TABLE activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_actor_id_fkey;

ALTER TABLE activity_logs
    ALTER COLUMN actor_id DROP NOT NULL;

ALTER TABLE activity_logs
    ADD CONSTRAINT activity_logs_actor_id_fkey
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_workspace_id_fkey;

ALTER TABLE activity_logs
    ADD CONSTRAINT activity_logs_workspace_id_fkey
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE;

ALTER TABLE activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_board_id_fkey;

ALTER TABLE activity_logs
    ADD CONSTRAINT activity_logs_board_id_fkey
        FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE SET NULL;

ALTER TABLE activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_task_id_fkey;

ALTER TABLE activity_logs
    ADD CONSTRAINT activity_logs_task_id_fkey
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL;

ALTER TABLE workspace_join_requests
    DROP CONSTRAINT IF EXISTS workspace_join_requests_reviewed_by_fkey;

ALTER TABLE workspace_join_requests
    ADD CONSTRAINT workspace_join_requests_reviewed_by_fkey
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL;
