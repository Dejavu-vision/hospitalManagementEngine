-- Grant DOCTOR and RECEPTIONIST roles access to the PATIENTS page key
-- this ensures both roles can see the unified patient browse/search page
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name IN ('ROLE_DOCTOR', 'ROLE_RECEPTIONIST')
  AND p.page_key = 'PATIENTS';
