-- Grant DOCTOR role access to the PATIENTS page key
-- so doctors can access the All Patients browse page
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name = 'ROLE_DOCTOR'
  AND p.page_key = 'PATIENTS';
