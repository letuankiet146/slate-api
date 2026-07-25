-- Mark default board columns so Done can stay pinned and rename/delete rules apply.
ALTER TABLE board_columns
    ADD COLUMN system_key VARCHAR(32) NULL;

UPDATE board_columns
SET system_key = 'TODO'
WHERE name = 'Cần làm' AND system_key IS NULL;

UPDATE board_columns
SET system_key = 'IN_PROGRESS'
WHERE name = 'Đang làm' AND system_key IS NULL;

UPDATE board_columns
SET system_key = 'DONE'
WHERE name = 'Hoàn thành' AND system_key IS NULL;

CREATE INDEX idx_board_columns_system_key ON board_columns (board_id, system_key);
