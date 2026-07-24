CREATE TABLE task_comments (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES users (id),
    author_name VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_comments_task_created ON task_comments (task_id, created_at ASC);
