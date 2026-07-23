-- Account types: OWNER (self-registered, can create workspaces) vs INTERNAL (created by workspace admin)
ALTER TABLE users
    ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'OWNER',
    ADD COLUMN google_sub VARCHAR(255),
    ADD COLUMN created_by_user_id UUID REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT users_account_type_check CHECK (account_type IN ('OWNER', 'INTERNAL'));

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE UNIQUE INDEX users_google_sub_unique ON users (google_sub) WHERE google_sub IS NOT NULL;

-- Workspace ownership: private workspaces belong to an owner account
ALTER TABLE workspaces
    ADD COLUMN owner_id UUID REFERENCES users (id);

UPDATE workspaces w
SET owner_id = COALESCE(
    (
        SELECT m.user_id
        FROM memberships m
                 JOIN roles r ON r.id = m.role_id
        WHERE m.workspace_id = w.id
          AND m.scope_type = 'WORKSPACE'
          AND r.code = 'WORKSPACE_ADMIN'
        ORDER BY m.created_at
        LIMIT 1
    ),
    w.created_by
)
WHERE owner_id IS NULL;

ALTER TABLE workspaces
    ALTER COLUMN owner_id SET NOT NULL;
