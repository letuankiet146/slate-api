DROP INDEX IF EXISTS idx_workspaces_company_email;

ALTER TABLE workspaces
    DROP COLUMN IF EXISTS company_email;
