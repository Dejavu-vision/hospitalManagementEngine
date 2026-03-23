## Access Management - Hospital Management Engine (Simple Spec)

This document defines what each role can see and do in the frontend. Use it as the source of truth for role-based UI and route guards.

### Roles
- **Receptionist**: Front-desk operations (patient onboarding, scheduling, billing handoff).
- **Doctor**: Clinical operations (consults, orders, notes).
- **Hospital Admin**: Configuration, staff/roles, dashboards, audits.

### Authentication
- Login yields a JWT/session with `role` in claims: one of `RECEPTIONIST`, `DOCTOR`, `HOSPITAL_ADMIN`.
- Frontend must gate routes/components with the role matrix below. Hide disallowed actions; block deep-links with an “Access denied” state.

### Core Entities (read/write expectations)
- **Patient**: demographics, contacts, insurance.
- **Appointment**: date/time, patient, doctor, reason, status.
- **Clinical Record**: diagnoses, vitals, orders, prescriptions, notes, lab/radiology results.
- **Billing Summary**: visit charges, payment status (no pricing logic in UI; just display status/refs).
- **Staff/User**: name, role, status.

### Permissions Matrix (R = Read, C = Create, U = Update, D = Delete, A = Action)
- **Receptionist**
  - Patient: R/C/U (admin-only D), limited fields (no clinical data)
  - Appointment: R/C/U/D
  - Clinical Record: R (header only: diagnoses list names redacted content), no C/U/D
  - Billing Summary: R
  - Staff/User: none
  - Actions: check‑in, check‑out, assign doctor, reschedule, cancel appointment
- **Doctor**
  - Patient: R (full, including history), no C/U/D of demographics except emergency contacts U
  - Appointment: R (own + assigned), U status (start/finish), no C/D
  - Clinical Record: R/C/U for own encounters; R for others (read-only); no D
  - Billing Summary: R (visit-level), no C/U/D
  - Staff/User: none
  - Actions: add diagnoses, write notes, order labs/imaging, prescribe meds, sign encounter
- **Hospital Admin**
  - Patient: R (full), no routine C/U/D of demographics
  - Appointment: R (all), no routine C/U/D (can manage via policy-only screens if needed)
  - Clinical Record: R (audit view), no C/U/D
  - Billing Summary: R (all), no C/U/D
  - Staff/User: R/C/U/D (manage accounts, roles)
  - Actions: configure departments, specialties, clinic hours, role policies, view audit logs, export reports

Notes:
- “Own encounters” = encounters where `doctorId == currentUserId`.
- Delete of patients/clinical data is generally disabled; use archive/void via backend if ever needed.

### Screens (routes) and Visibility
- `/login` (All, unauthenticated)
- `/dashboard`
  - Receptionist: Today’s appointments, quick check‑in/out, new patient button
  - Doctor: My schedule, pending tasks (unsigned notes, results to review)
  - Hospital Admin: Metrics (patients today, utilization), quick links to admin
- `/patients`
  - Receptionist: list/search; create/edit demographics
  - Doctor: list/search; read-only demographics; edit emergency contacts only
  - Hospital Admin: read-only
- `/patients/:id`
  - Receptionist: demographics tab (R/W), clinical tab (headers only, redacted body)
  - Doctor: demographics (R), clinical timeline (R/W for own, R for others)
  - Hospital Admin: read-only (with audit subtab)
- `/appointments`
  - Receptionist: full CRUD, assign doctor, status changes
  - Doctor: read-only list (own + assigned), can start/finish visit
  - Hospital Admin: read-only
- `/appointments/new`
  - Receptionist only
- `/appointments/:id`
  - Receptionist: reschedule/cancel/check‑in/out
  - Doctor: start/finish, open encounter
  - Hospital Admin: read-only
- `/encounters/:id`
  - Doctor: edit notes, diagnoses, orders, prescriptions; sign encounter
  - Receptionist/Admin: read-only (Receptionist: headers only; Admin: full read with audit)
- `/orders` (lab/radiology)
  - Doctor: place/view/cancel own orders
  - Receptionist: view status for logistics
  - Admin: read-only
- `/billing`
  - Receptionist: read-only summary to confirm handoff
  - Doctor: read-only
  - Admin: read-only across org
- `/admin`
  - Hospital Admin only: users/roles, departments, clinic hours, audits, exports

### Component-level Rules (UI guards)
- Hide action buttons if not permitted; disable with tooltip on hover if needed.
- If a user reaches a forbidden route, show a centered “Access denied” with a back link.
- For receptionist clinical previews, show only: encounter date, doctor name, high-level reason; never notes/content/body.
- Doctor cannot edit patient demographics except emergency contacts; present that as a separate edit section.
- Admin audit views must be read-only; include “Export CSV” when available from backend.

### Field-level Access (examples)
- Patient
  - Receptionist (R/W): name, DOB, gender, phone, email, address, insurance
  - Doctor (R): all above; (W): emergency contacts only
  - Admin (R): all above
- Clinical Record
  - Receptionist: R headers only (date, provider, type)
  - Doctor: R/W own encounters; R others
  - Admin: R (audit)

### Navigation Defaults after Login
- Receptionist -> `/dashboard` (receptionist variant)
- Doctor -> `/dashboard` (doctor variant)
- Hospital Admin -> `/dashboard` (admin variant)

### Error/Empty States
- If no permission: show access denied state.
- If entity exists but fields are restricted: show masked/redacted indicators instead of blank.

### Frontend Integration Notes
- Store `role` in auth context; expose helpers:
  - `canRead(entity, context?)`, `canWrite(entity, context?)`, `canPerform(action, context?)`
  - `isDoctor()`, `isReceptionist()`, `isHospitalAdmin()`
- Route guards wrap pages; component guards wrap action areas/buttons.
- Prefer “not shown” over “disabled” for sensitive actions.

### Assumptions (keep UI simple)
- Single role per user session.
- Doctor edits only within active encounter; signing locks that encounter from further edits.
- Deletion is rare; prefer cancel/archive where presented.

