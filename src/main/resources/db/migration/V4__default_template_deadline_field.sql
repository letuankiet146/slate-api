-- Add Deadline field to existing default task templates that don't have it yet
INSERT INTO field_definitions (id, template_id, name, field_type, required, editable, visibility, position, created_at)
SELECT
    gen_random_uuid(),
    tt.id,
    'Deadline',
    'DATE',
    FALSE,
    TRUE,
    'INTERNAL',
    COALESCE(
        (SELECT MAX(fd.position) + 1 FROM field_definitions fd WHERE fd.template_id = tt.id),
        0
    ),
    NOW()
FROM task_templates tt
WHERE LOWER(tt.name) = LOWER('Mặc định')
  AND NOT EXISTS (
    SELECT 1
    FROM field_definitions fd
    WHERE fd.template_id = tt.id
      AND LOWER(fd.name) = LOWER('Deadline')
  );
