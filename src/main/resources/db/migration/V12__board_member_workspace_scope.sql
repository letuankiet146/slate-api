-- Board members are invited at workspace scope; per-board access follows task assignment.

INSERT INTO memberships (id, user_id, role_id, scope_type, workspace_id, board_id, created_at)
SELECT gen_random_uuid(), sub.user_id, sub.role_id, 'WORKSPACE', sub.workspace_id, NULL, NOW()
FROM (
    SELECT DISTINCT m.user_id, m.role_id, b.workspace_id
    FROM memberships m
    JOIN boards b ON m.board_id = b.id
    JOIN roles r ON m.role_id = r.id
    WHERE m.scope_type = 'BOARD'
      AND r.code = 'BOARD_MEMBER'
) sub
WHERE NOT EXISTS (
    SELECT 1
    FROM memberships wm
    JOIN roles wr ON wm.role_id = wr.id
    WHERE wm.scope_type = 'WORKSPACE'
      AND wm.workspace_id = sub.workspace_id
      AND wm.user_id = sub.user_id
      AND wr.code = 'BOARD_MEMBER'
);

DELETE FROM memberships m
USING roles r
WHERE m.role_id = r.id
  AND m.scope_type = 'BOARD'
  AND r.code = 'BOARD_MEMBER'
  AND NOT EXISTS (
      SELECT 1
      FROM tasks t
      WHERE t.board_id = m.board_id
        AND t.assignee_id = m.user_id
        AND t.deleted_at IS NULL
  );
