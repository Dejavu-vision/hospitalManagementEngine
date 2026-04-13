-- Grant DOCTOR and RECEPTIONIST roles access to the DOCTORS page key
-- this ensures receptionists can manage all doctors and doctors can manage their status
INSERT IGNORE INTO role_pages (role_id, page_id)
  SELECT r.id, p.id FROM roles r, ui_pages p
  WHERE r.name IN ('ROLE_DOCTOR', 'ROLE_RECEPTIONIST')
  AND p.page_key = 'DOCTORS';
