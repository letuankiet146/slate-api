-- Schema
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    locale          VARCHAR(10) NOT NULL DEFAULT 'vi',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE workspaces (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    key         VARCHAR(32) NOT NULL UNIQUE,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ NULL,
    deleted_by  UUID REFERENCES users(id)
);

CREATE TABLE boards (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ NULL,
    deleted_by   UUID REFERENCES users(id)
);

CREATE INDEX idx_boards_workspace ON boards(workspace_id);
CREATE INDEX idx_boards_workspace_deleted ON boards(workspace_id, deleted_at);
CREATE INDEX idx_boards_deleted_at ON boards(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE board_columns (
    id         UUID PRIMARY KEY,
    board_id   UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    position   INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_columns_board ON board_columns(board_id);

CREATE TABLE memberships (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id      UUID NOT NULL REFERENCES roles(id),
    scope_type   VARCHAR(32) NOT NULL,
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    board_id     UUID REFERENCES boards(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_membership_scope CHECK (
        (scope_type = 'SYSTEM' AND workspace_id IS NULL AND board_id IS NULL)
        OR (scope_type = 'WORKSPACE' AND workspace_id IS NOT NULL AND board_id IS NULL)
        OR (scope_type = 'BOARD' AND board_id IS NOT NULL)
    )
);

CREATE INDEX idx_memberships_user ON memberships(user_id);
CREATE INDEX idx_memberships_workspace ON memberships(workspace_id);
CREATE INDEX idx_memberships_board ON memberships(board_id);
CREATE INDEX idx_workspaces_deleted_at ON workspaces(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE task_templates (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_task_templates_workspace_name UNIQUE (workspace_id, name)
);

CREATE INDEX idx_task_templates_workspace ON task_templates(workspace_id);

CREATE TABLE task_template_boards (
    template_id UUID NOT NULL REFERENCES task_templates(id) ON DELETE CASCADE,
    board_id    UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    PRIMARY KEY (template_id, board_id)
);

CREATE INDEX idx_task_template_boards_board ON task_template_boards(board_id);

CREATE TABLE tasks (
    id           UUID PRIMARY KEY,
    board_id     UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    column_id    UUID REFERENCES board_columns(id),
    title        VARCHAR(512) NOT NULL,
    description  TEXT,
    created_by   UUID NOT NULL REFERENCES users(id),
    assignee_id  UUID REFERENCES users(id),
    template_id  UUID REFERENCES task_templates(id),
    position     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ NULL,
    deleted_by   UUID REFERENCES users(id)
);

CREATE INDEX idx_tasks_board ON tasks(board_id);
CREATE INDEX idx_tasks_column ON tasks(column_id);
CREATE INDEX idx_tasks_template ON tasks(template_id);
CREATE INDEX idx_tasks_board_deleted ON tasks(board_id, deleted_at);
CREATE INDEX idx_tasks_deleted_at ON tasks(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE field_definitions (
    id           UUID PRIMARY KEY,
    board_id     UUID REFERENCES boards(id) ON DELETE CASCADE,
    task_id      UUID REFERENCES tasks(id) ON DELETE CASCADE,
    template_id  UUID REFERENCES task_templates(id) ON DELETE CASCADE,
    name         VARCHAR(128) NOT NULL,
    field_type   VARCHAR(32) NOT NULL,
    required     BOOLEAN NOT NULL DEFAULT FALSE,
    editable     BOOLEAN NOT NULL DEFAULT TRUE,
    visibility   VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    position     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_field_defs_board ON field_definitions(board_id);
CREATE INDEX idx_field_defs_task ON field_definitions(task_id);
CREATE INDEX idx_field_defs_template ON field_definitions(template_id);

CREATE TABLE field_required_column_names (
    field_definition_id UUID NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    column_name         VARCHAR(128) NOT NULL,
    PRIMARY KEY (field_definition_id, column_name)
);

CREATE TABLE task_field_values (
    id                  UUID PRIMARY KEY,
    task_id             UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    field_definition_id UUID NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    value               TEXT,
    UNIQUE (task_id, field_definition_id)
);

CREATE INDEX idx_task_field_values_task ON task_field_values(task_id);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Seed roles and permissions
INSERT INTO permissions (id, code, description) VALUES
    ('11111111-1111-1111-1111-111111111001', 'USER_MANAGE', 'Create and manage users'),
    ('11111111-1111-1111-1111-111111111002', 'WORKSPACE_MANAGE', 'Create and manage workspaces and members'),
    ('11111111-1111-1111-1111-111111111003', 'BOARD_MANAGE', 'Manage boards, columns, and field definitions'),
    ('11111111-1111-1111-1111-111111111004', 'TASK_CREATE', 'Create tasks on a board'),
    ('11111111-1111-1111-1111-111111111005', 'TASK_UPDATE', 'Update tasks and move status'),
    ('11111111-1111-1111-1111-111111111006', 'TASK_VIEW', 'View internal tasks and fields'),
    ('11111111-1111-1111-1111-111111111007', 'TASK_VIEW_PUBLIC', 'View public fields only');

INSERT INTO roles (id, code, name, description) VALUES
    ('22222222-2222-2222-2222-222222222001', 'SYSTEM_ADMIN', 'System Admin', 'Full system access'),
    ('22222222-2222-2222-2222-222222222002', 'WORKSPACE_ADMIN', 'Workspace Admin', 'Manage workspace and boards'),
    ('22222222-2222-2222-2222-222222222003', 'BOARD_MEMBER', 'Board Member', 'Create and update tasks'),
    ('22222222-2222-2222-2222-222222222004', 'BOARD_VIEWER', 'Board Viewer', 'View tasks only');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '22222222-2222-2222-2222-222222222001', id FROM permissions;

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('22222222-2222-2222-2222-222222222002', '11111111-1111-1111-1111-111111111002'),
    ('22222222-2222-2222-2222-222222222002', '11111111-1111-1111-1111-111111111003'),
    ('22222222-2222-2222-2222-222222222002', '11111111-1111-1111-1111-111111111004'),
    ('22222222-2222-2222-2222-222222222002', '11111111-1111-1111-1111-111111111005'),
    ('22222222-2222-2222-2222-222222222002', '11111111-1111-1111-1111-111111111006'),
    ('22222222-2222-2222-2222-222222222003', '11111111-1111-1111-1111-111111111004'),
    ('22222222-2222-2222-2222-222222222003', '11111111-1111-1111-1111-111111111005'),
    ('22222222-2222-2222-2222-222222222003', '11111111-1111-1111-1111-111111111006'),
    ('22222222-2222-2222-2222-222222222004', '11111111-1111-1111-1111-111111111006'),
    ('22222222-2222-2222-2222-222222222004', '11111111-1111-1111-1111-111111111007');
