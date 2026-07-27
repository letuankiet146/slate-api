-- RBAC overhaul: BOARD_ADMIN role, unified users, must-change-password, drop join requests

-- BOARD_ADMIN role
INSERT INTO roles (id, code, name, description)
VALUES (
    '22222222-2222-2222-2222-222222222005',
    'BOARD_ADMIN',
    'Board Admin',
    'Manage assigned boards and tasks'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'BOARD_ADMIN'
  AND p.code IN ('BOARD_MANAGE', 'TASK_CREATE', 'TASK_UPDATE', 'TASK_VIEW')
ON CONFLICT DO NOTHING;

-- BOARD_MEMBER: remove TASK_CREATE (members cannot create tasks)
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE code = 'BOARD_MEMBER')
  AND permission_id = (SELECT id FROM permissions WHERE code = 'TASK_CREATE');

-- Force password change for provisioned users
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing INTERNAL users provisioned by admins should change password on next login
UPDATE users SET must_change_password = TRUE WHERE account_type = 'INTERNAL';

-- Drop account type distinction
ALTER TABLE users DROP COLUMN IF EXISTS account_type;

-- Drop join request feature
DROP TABLE IF EXISTS workspace_join_requests;

-- Ensure workspace creators (former OWNER) retain workspace admin membership
INSERT INTO memberships (id, user_id, role_id, scope_type, workspace_id, board_id, created_at)
SELECT
    gen_random_uuid(),
    w.created_by,
    (SELECT id FROM roles WHERE code = 'WORKSPACE_ADMIN'),
    'WORKSPACE',
    w.id,
    NULL,
    NOW()
FROM workspaces w
WHERE w.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM memberships m
      JOIN roles r ON r.id = m.role_id
      WHERE m.user_id = w.created_by
        AND m.workspace_id = w.id
        AND m.scope_type = 'WORKSPACE'
        AND r.code = 'WORKSPACE_ADMIN'
  );
