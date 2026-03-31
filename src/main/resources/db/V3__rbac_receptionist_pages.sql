-- ui_pages entries for receptionist screens
INSERT IGNORE INTO ui_pages (page_key, page_name, description) VALUES
  ('RECEPTIONIST_DASHBOARD',    'Reception Dashboard',  'Receptionist daily stats and quick actions'),
  ('RECEPTIONIST_QUEUE',        'Queue Management',     'Live queue across all doctors'),
  ('RECEPTIONIST_PATIENTS',     'Patient Management',   'Search, register, and view patients'),
  ('RECEPTIONIST_APPOINTMENTS', 'Appointment Booking',  'Book scheduled and walk-in appointments');

-- role_pages entries for RECEPTIONIST role
-- Note: role_id for RECEPTIONIST may vary; use a subquery to be safe
INSERT IGNORE INTO role_pages (role_id, page_key)
SELECT r.id, p.page_key
FROM roles r
CROSS JOIN (
  SELECT 'RECEPTIONIST_DASHBOARD'    AS page_key UNION ALL
  SELECT 'RECEPTIONIST_QUEUE'                    UNION ALL
  SELECT 'RECEPTIONIST_PATIENTS'                 UNION ALL
  SELECT 'RECEPTIONIST_APPOINTMENTS'
) p
WHERE r.name = 'RECEPTIONIST';
