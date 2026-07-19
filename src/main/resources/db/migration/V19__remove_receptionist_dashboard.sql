-- Delete mapping for RECEPTIONIST_DASHBOARD page
DELETE FROM role_pages WHERE page_id = (SELECT id FROM ui_pages WHERE page_key = 'RECEPTIONIST_DASHBOARD');

-- Delete the page itself from ui_pages
DELETE FROM ui_pages WHERE page_key = 'RECEPTIONIST_DASHBOARD';
