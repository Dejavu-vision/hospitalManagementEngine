-- Remove receptionist dashboard only when the UI page tables exist.
-- These tables are not present in all existing database installations.

SET @ui_pages_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'ui_pages'
);

SET @role_pages_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'role_pages'
);

SET @sql = IF(
    @role_pages_exists > 0 AND @ui_pages_exists > 0,
    'DELETE FROM role_pages WHERE page_id = (SELECT id FROM ui_pages WHERE page_key = ''RECEPTIONIST_DASHBOARD'')',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    @ui_pages_exists > 0,
    'DELETE FROM ui_pages WHERE page_key = ''RECEPTIONIST_DASHBOARD''',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
