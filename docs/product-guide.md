# CuraMatrix HSM — Hospital Management System
## Phase-wise Feature Build Guide
**Version 1.0 | Internal Use Only**

---

> **Summary:** 5 Phases · 40+ Features · 12 API Modules · 4 User Roles · 21+ Weeks Estimated

---

## Table of Contents

1. [System Overview & Architecture](#1-system-overview--architecture)
2. [Phase 1 — Foundation & Authentication](#2-phase-1--foundation--authentication-weeks-13)
3. [Phase 2 — User & Access Management](#3-phase-2--user--access-management-weeks-47)
4. [Phase 3 — Patient & Appointment Management](#4-phase-3--patient--appointment-management-weeks-812)
5. [Phase 4 — Clinical Workflow](#5-phase-4--clinical-workflow-weeks-1317)
6. [Phase 5 — Platform Hardening & Reporting](#6-phase-5--platform-hardening--reporting-weeks-1821)
7. [Complete Feature Checklist](#7-complete-feature-checklist)
8. [Timeline Summary](#8-timeline-summary)

---

## 1. System Overview & Architecture

CuraMatrix HSM is a **multi-tenant hospital management platform**. Multiple hospitals share one deployed application, but all data is strictly isolated per tenant using `tenant_id`. The platform supports the complete outpatient workflow from hospital onboarding to patient discharge.

### 1.1 Platform Roles

| Role | Scope | Key Responsibilities |
|------|-------|----------------------|
| **Super Admin** | Platform-wide | Onboards hospitals, manages subscriptions, suspends/activates tenants |
| **Admin** | Per Hospital | Manages users, roles, page-level access, and audit logs |
| **Receptionist** | Per Hospital | Registers patients, books appointments (scheduled & walk-in) |
| **Doctor** | Per Hospital | Manages consultation queue, creates diagnosis & prescriptions |

### 1.2 Subscription Plans

| Plan | Monthly Price | Max Users | Max Patients | Storage | API Calls/Hr |
|------|--------------|-----------|--------------|---------|--------------|
| BASIC | $99/mo | 10 | 1,000 | 5 GB | 1,000 |
| STANDARD | $499/mo | 50 | 10,000 | 50 GB | 10,000 |
| PREMIUM | $1,999/mo | Unlimited | Unlimited | 500 GB | Unlimited |

> **Important:** When a limit is reached, new create operations are blocked. Expired or suspended hospitals cannot log in.

### 1.3 Core Workflow

```
Hospital onboarded by Super Admin
    ↓
Admin and staff users created
    ↓
Receptionist registers patient
    ↓
Receptionist books appointment (scheduled / walk-in)
    ↓
Appointment: BOOKED → CHECKED_IN → IN_PROGRESS
    ↓
Doctor creates diagnosis and prescriptions
    ↓
Appointment: IN_PROGRESS → COMPLETED (or CANCELLED)
```

### 1.4 Appointment Status Lifecycle

| From | To | Who | When |
|------|----|-----|------|
| — | BOOKED | Receptionist | On appointment creation |
| BOOKED | CHECKED_IN | Receptionist | Patient arrives at facility |
| CHECKED_IN | IN_PROGRESS | Doctor / Receptionist | Doctor begins consultation |
| IN_PROGRESS | COMPLETED | Doctor | Consultation ends |
| BOOKED / CHECKED_IN | CANCELLED | Receptionist / Admin | Appointment cannot proceed |

---

## 2. Phase 1 — Foundation & Authentication _(Weeks 1–3)_

> Establishes the core infrastructure: multi-tenancy, authentication, and platform-level hospital management. **Nothing else works without this phase.**

---

### 2.1 Multi-Tenant Architecture

#### Backend Features

- Tenant isolation middleware that injects `tenant_id` on every request
- Database schema with `tenant_id` columns on all tenant-scoped tables
- Tenant context propagation via JWT claims
- Row-level data filtering so no tenant sees another's data

#### What to Build

| Feature | Description | APIs Involved |
|---------|-------------|---------------|
| Tenant Context Middleware | Reads `tenant_id` from JWT, applies to all DB queries automatically | All authenticated routes |
| JWT Auth Service | Issues signed tokens with `tenant_id`, role, and page permissions embedded | `POST /api/auth/login` |
| Token Refresh | Refresh endpoint to extend sessions without re-login | `POST /api/auth/refresh` |
| Password Encryption | bcrypt hashing for all stored passwords | Internal |
| Login Blocking | Block login if subscription expired or hospital suspended | `POST /api/auth/login` |

---

### 2.2 Super Admin — Tenant Management

The Super Admin operates outside any hospital tenant and manages the platform as a whole.

#### Features to Build

- **Hospital Registration** — create a new tenant with name, contact, plan, and initial admin credentials
- **Hospital Listing** — paginated list with filters (active, suspended, plan type)
- **Hospital Detail View** — full profile with user counts, patient counts, subscription status
- **Subscription Management** — upgrade/downgrade plan, update billing cycle
- **Suspend Hospital** — blocks all logins for that tenant immediately
- **Activate Hospital** — re-enables a suspended hospital
- **Reset Tenant Admin Password** — Super Admin can reset the password for any hospital's admin
- **Quota Monitoring** — display current usage vs plan limits (users, patients, storage)

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| List tenants | `GET /api/super-admin/tenants` | Supports filters and pagination |
| Create tenant | `POST /api/super-admin/tenants` | Creates hospital + admin user atomically |
| Update tenant | `PUT /api/super-admin/tenants/{id}` | Plan, contact info, limits |
| Suspend tenant | `PUT /api/super-admin/tenants/{id}/suspend` | Blocks login immediately |
| Activate tenant | `PUT /api/super-admin/tenants/{id}/activate` | Re-enables access |
| Reset admin password | `PUT /api/super-admin/tenants/{id}/admin/password` | Super Admin only |

---

### 2.3 Frontend Screens — Phase 1

| Screen | Role | Key Elements |
|--------|------|--------------|
| Login Page | All roles | Email/password form, error states, subscription expiry message |
| Super Admin Dashboard | Super Admin | Tenant summary cards, quota gauges, recent activity |
| Tenant List | Super Admin | Table with status badges, search, filter by plan/status |
| Add / Edit Hospital | Super Admin | Multi-step form: hospital info → plan → admin account |
| Tenant Detail | Super Admin | Usage stats, user list preview, subscription timeline |

---

### 2.4 Phase 1 Acceptance Criteria

- [ ] Login issues a valid JWT with correct `tenant_id`, roles, and expiry
- [ ] Suspended or expired hospital cannot log in — returns clear error message
- [ ] Super Admin can create a hospital and immediately log in as that hospital's admin
- [ ] All data queries are automatically scoped to `tenant_id` — cross-tenant data leaks are impossible
- [ ] Plan limits are stored and readable; enforcement comes in Phase 2

---

## 3. Phase 2 — User & Access Management _(Weeks 4–7)_

> Builds the full user lifecycle and layered access control system inside each hospital. Admins gain complete control over who can access what.

---

### 3.1 User Management (Admin)

#### Features to Build

- **Create User** — with employee ID, name, email, password, and role assignment
- **List Users** — paginated, searchable by name/employee ID, filterable by role and status
- **Update User** — edit profile details, department, contact info
- **Activate / Deactivate User** — soft toggle; deactivated users cannot log in
- **Delete User** — hard delete with referential integrity checks
- **User Detail** — full profile with current roles and custom page access
- **Plan Limit Enforcement** — block user creation when max users limit is reached

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| List users | `GET /api/admin/users` | Filtered, paginated |
| Create user | `POST /api/admin/users` | Enforces plan user limit |
| Update user | `PUT /api/admin/users/{id}` | Profile fields only |
| Delete user | `DELETE /api/admin/users/{id}` | Checks no active appointments |
| Activate user | `PUT /api/admin/users/{id}/activate` | |
| Deactivate user | `PUT /api/admin/users/{id}/deactivate` | |

---

### 3.2 Role Management

#### Roles in the System

| Role | Description |
|------|-------------|
| `SUPER_ADMIN` | Platform owner, full access |
| `ADMIN` | Hospital admin, manages users and access |
| `DOCTOR` | Clinical role, consultation access |
| `RECEPTIONIST` | Front-desk role, patient and appointment access |

#### Features to Build

- **Assign Role** — assign one or more roles to a user
- **Remove Role** — revoke a specific role from a user
- **Role Listing** — show all roles and their assigned users
- **Multi-role support** — a user can hold multiple roles simultaneously

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Set all roles | `PUT /api/admin/users/{id}/roles` | Replaces full role set |
| Add single role | `POST /api/admin/users/{id}/roles/{roleName}` | Additive |
| Remove single role | `DELETE /api/admin/users/{id}/roles/{roleName}` | |

---

### 3.3 Page-Level Access Control

The most sophisticated feature in this phase. Access is applied in layers:

```
Role Default Access
    ↓
Role Page Rules (admin overrides per role)
    ↓
User Page Overrides (grant extra or deny specific pages per user)
    ↓
Effective Access (final computed set for the user)
```

#### Access Layers Explained

| Layer | Description |
|-------|-------------|
| **Role Access** | Certain pages are enabled for all users of a role by default |
| **Role Page Rules** | Admin can grant or deny specific pages per role |
| **User Page Grant** | Admin grants specific additional pages to an individual user |
| **User Page Deny** | Admin denies specific pages from an individual user (overrides role) |
| **Effective Access** | Final merged result — what the user actually sees |

#### Features to Build

- **Page Registry** — list all pages/routes in the system with slugs and display names
- **Role Page Assignment** — assign allowed pages to each role
- **User Page Grant** — grant specific additional pages to an individual user
- **User Page Deny** — deny specific pages from an individual user (overrides role)
- **Effective Access Computation** — merge role + user rules to get final access set
- **My Access Endpoint** — returns the effective page list for the logged-in user (used by frontend)
- **Audit Log** — track all access changes: who changed, what page, which user/role, timestamp

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Get my access | `GET /api/me/access` | Returns effective page slugs for current user |
| List page registry | `GET /api/admin/access/pages` | All system pages |
| Set role page access | `PUT /api/admin/access/roles/{role}` | Bulk update for role |
| Grant page to user | `POST /api/admin/access/users/{id}/grant` | User-level override |
| Deny page for user | `POST /api/admin/access/users/{id}/deny` | User-level override |
| Get access audit log | `GET /api/admin/access/audit` | Paginated log with filters |

---

### 3.4 Frontend Screens — Phase 2

| Screen | Role | Key Elements |
|--------|------|--------------|
| User List | Admin | Table with role badges, status toggle, search, add button |
| Add / Edit User | Admin | Form with role picker, employee ID, department |
| User Detail | Admin | Profile, roles, page overrides in tabs |
| Role Management | Admin | Role cards with user count and page count |
| Access Management | Admin | Page tree with role toggles and user-level overrides |
| Access Audit Log | Admin | Timestamped change log with actor and target info |

---

### 3.5 Phase 2 Acceptance Criteria

- [ ] Admin cannot create users beyond the plan's user limit
- [ ] Deactivated user cannot log in
- [ ] A user with DOCTOR + RECEPTIONIST roles sees the union of both role pages
- [ ] A user-level deny overrides their role grant for that specific page
- [ ] `GET /api/me/access` returns the correct effective page set, cached efficiently
- [ ] Every access change is recorded in the audit log with actor, timestamp, and target

---

## 4. Phase 3 — Patient & Appointment Management _(Weeks 8–12)_

> The operational heart of the system. Receptionists register patients and manage the appointment lifecycle from booking through check-in.

---

### 4.1 Patient Registration

#### Features to Build

- **Register Patient** — name, date of birth, gender, contact details, address, emergency contact
- **Patient Search** — fast search by name, phone, or patient ID (used during appointment booking)
- **Patient Profile View** — full details with appointment history
- **Update Patient** — edit any profile field
- **Patient Limit Enforcement** — block registration when plan's max patient limit is reached
- **Duplicate Detection** — warn when a patient with same name + DOB already exists

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Register patient | `POST /api/patients` | Enforces plan patient limit |
| Search patients | `GET /api/patients?query=` | Name, phone, or ID |
| Get patient | `GET /api/patients/{id}` | Full profile |
| Update patient | `PUT /api/patients/{id}` | All profile fields |

---

### 4.2 Scheduled Appointments

#### How It Works

- Appointment is booked for a specific doctor, date, and **30-minute time slot**
- Slots run from **09:00 to 17:00** — 16 slots per doctor per day
- A slot is **blocked once booked** — no double-booking allowed for same doctor + date + time
- Receptionist selects a date and doctor, then picks from available slots

#### Features to Build

- **Slot Availability** — fetch available (unbooked) slots for a doctor on a date
- **Book Appointment** — link patient + doctor + date + slot, initial status `BOOKED`
- **View Appointment** — full appointment detail with patient and doctor info
- **Appointment List** — filterable by date, doctor, status, department
- **Update Appointment Status** — `BOOKED → CHECKED_IN → IN_PROGRESS → COMPLETED / CANCELLED`
- **Cancel Appointment** — sets status to `CANCELLED`, frees the slot

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Get available slots | `GET /api/appointments/doctor/{id}/slots?date=` | Returns only unbooked slots |
| Book appointment | `POST /api/appointments` | Validates slot availability |
| List appointments | `GET /api/appointments` | Multiple filter options |
| Get appointment | `GET /api/appointments/{id}` | Full detail |
| Update status | `PUT /api/appointments/{id}/status` | Validates status transitions |

---

### 4.3 Walk-in Appointments

#### How It Works

- Created for the **current date only** — no future date booking
- **No time slot** — patients are seen in token order
- Token number is **auto-generated**: starts at 1 per doctor per day, increments per new walk-in
- Doctor sees walk-ins in their queue alongside scheduled appointments

#### Features to Build

- **Create Walk-in** — select patient and doctor, system assigns next token number
- **Walk-in Queue View** — ordered list by token for the selected doctor
- **Token Display** — show token number prominently for patient tracking
- **Mixed Queue** — doctor's view shows both scheduled and walk-in appointments sorted by time/token

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Create walk-in | `POST /api/appointments/walk-in` | Auto-generates token number |
| Doctor today queue | `GET /api/appointments/doctor/{id}/today` | Both types, sorted |

---

### 4.4 Department Management

#### Features to Build

- List all departments in the hospital
- View department detail with assigned doctors
- Receptionists use department list to filter doctors during booking

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| List departments | `GET /api/departments` | All hospital departments |
| Get department | `GET /api/departments/{id}` | With doctor list |

---

### 4.5 Frontend Screens — Phase 3

| Screen | Role | Key Elements |
|--------|------|--------------|
| Patient Search / Registration | Receptionist | Search bar with live results; register form if not found |
| Patient Profile | Receptionist | Details, edit button, appointment history |
| Book Appointment | Receptionist | Department → Doctor → Date → Slot picker |
| Walk-in Creation | Receptionist | Patient search + doctor picker → token display |
| Appointment List | Receptionist / Admin | Tabular view with status filters, date range, doctor filter |
| Appointment Detail | Receptionist / Doctor | Full info, status update button, history log |
| Department List | Receptionist | Cards with doctor count and specialty info |

---

### 4.6 Phase 3 Acceptance Criteria

- [ ] Booking the same doctor + date + slot twice returns a conflict error
- [ ] Walk-in token increments correctly across concurrent walk-ins (no duplicate tokens)
- [ ] Patient search returns results within 300ms for datasets up to 100,000 patients
- [ ] Status transitions are validated — cannot jump from BOOKED to COMPLETED
- [ ] Patient limit enforcement blocks registration and shows clear error when plan limit is reached
- [ ] Department filter in booking shows only doctors belonging to that department

---

## 5. Phase 4 — Clinical Workflow _(Weeks 13–17)_

> Gives doctors everything they need during a consultation: a live queue, diagnosis creation, and prescription management with medicine autocomplete.

---

### 5.1 Doctor Queue (Today's View)

#### Features to Build

- **Today's Queue** — all appointments for the doctor today, sorted by scheduled time / token number
- **Status Filters** — filter by CHECKED_IN, IN_PROGRESS, BOOKED, COMPLETED
- **Quick Status Update** — change appointment status directly from the queue card
- **Patient Summary Card** — show patient name, age, appointment type, token/time at a glance
- **Real-time-friendly** — queue should be easily refreshable or auto-refreshing

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Get today's queue | `GET /api/appointments/doctor/{id}/today` | Both scheduled and walk-in |
| Update status | `PUT /api/appointments/{id}/status` | Quick action from queue |

---

### 5.2 Diagnosis Management

#### Business Rules

- **Exactly one diagnosis** record per appointment — no duplicates allowed
- Diagnosis can be created only when appointment is `IN_PROGRESS`
- Doctor can update diagnosis until appointment is `COMPLETED`
- Severity levels: `MILD`, `MODERATE`, `SEVERE`, `CRITICAL`

#### Features to Build

- **Create Diagnosis** — chief complaint, findings, diagnosis text, severity level, notes
- **Update Diagnosis** — edit any field while appointment is still open
- **View Diagnosis** — full details linked to appointment
- **Diagnosis History** — all past diagnoses for a patient (read-only reference)

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Create diagnosis | `POST /api/diagnoses` | One per appointment |
| Get diagnosis | `GET /api/diagnoses/{id}` | Full record |
| Update diagnosis | `PUT /api/diagnoses/{id}` | While appointment is open |

---

### 5.3 Prescription Management

#### Business Rules

- Prescriptions are linked to a **diagnosis** (not directly to an appointment)
- Multiple medicines can be prescribed in a **single batch request**
- Each prescription line includes: medicine, dosage, frequency, duration, instructions
- Doctor can view all prescriptions for a diagnosis

#### Features to Build

- **Add Prescriptions** — batch add multiple medicine lines in one operation
- **View Prescriptions** — list all prescriptions for a diagnosis
- **Medicine Autocomplete Search** — fast search by medicine name (min 2 characters)
- **Prescription Print View** — formatted prescription ready for printing or PDF export

#### API Mapping

| Operation | Method & Endpoint | Notes |
|-----------|-------------------|-------|
| Add prescriptions | `POST /api/prescriptions` | Batch — multiple medicines at once |
| Get prescriptions | `GET /api/prescriptions/diagnosis/{diagnosisId}` | All for a diagnosis |
| Search medicines | `GET /api/medicines/search?query=` | Min 2 chars, fast autocomplete |

---

### 5.4 Medicine Catalog

- Central medicine catalog **shared across the entire platform** (not per-tenant)
- Super Admin manages the catalog — add, update, deactivate medicines
- Search is optimized for speed — results within **100ms**
- Each medicine has: name, generic name, type (tablet/syrup/injection), strength, unit

---

### 5.5 Frontend Screens — Phase 4

| Screen | Role | Key Elements |
|--------|------|--------------|
| Doctor Dashboard / Queue | Doctor | Card-per-patient queue, status badges, quick action buttons |
| Consultation View | Doctor | Split panel: patient info + diagnosis form + prescription builder |
| Diagnosis Form | Doctor | Severity selector, text fields, auto-save draft |
| Prescription Builder | Doctor | Medicine search (autocomplete), dosage/frequency inputs, batch add |
| Prescription View | Doctor / Patient | Formatted list ready for print with doctor letterhead |
| Medicine Search | Doctor | Inline autocomplete dropdown during prescription entry |
| Patient History (Doctor) | Doctor | Past diagnoses and prescriptions across all visits |

---

### 5.6 Phase 4 Acceptance Criteria

- [ ] Only one diagnosis can be created per appointment — second attempt returns a conflict error
- [ ] Medicine search returns results in under 100ms for a catalog of 10,000+ medicines
- [ ] Batch prescription saves all lines atomically — partial failure rolls back all
- [ ] Prescription print view renders correctly without requiring any additional data fetch
- [ ] Doctor cannot create or edit diagnosis for a COMPLETED appointment
- [ ] Today's queue correctly merges scheduled appointments and walk-ins in the right order

---

## 6. Phase 5 — Platform Hardening & Reporting _(Weeks 18–21+)_

> Elevates the platform from functional to production-grade: performance, observability, reporting, and security hardening.

---

### 6.1 Quota & Limit Enforcement (Hardening)

- Enforce all plan limits in real time **at the API layer**, not just in UI
- User creation blocked when max users reached — HTTP 403 with clear error payload
- Patient registration blocked when max patients reached
- API call rate limiting per tenant based on plan (1K / 10K / Unlimited per hour)
- Storage quota tracking for uploaded files and attachments
- Admin dashboard widget showing current usage vs limits with progress bars

---

### 6.2 Reporting & Analytics

#### Super Admin Reports

- Platform usage summary — total tenants, active hospitals, total patients across platform
- Revenue report — subscriptions by plan type, expiring plans, renewal pipeline
- Tenant activity heatmap — which hospitals are most active

#### Hospital Admin Reports

- Daily/weekly/monthly appointment volume by doctor and department
- Patient registration trends
- Appointment completion rate vs cancellation rate
- Doctor workload report — appointments completed per doctor
- Top diagnoses by severity and frequency

#### API Mapping

| Report | Method & Endpoint | Notes |
|--------|-------------------|-------|
| Platform summary | `GET /api/super-admin/reports/summary` | Super Admin only |
| Tenant activity | `GET /api/super-admin/reports/tenants` | Usage and plan data |
| Appointment stats | `GET /api/admin/reports/appointments` | Date range, doctor filter |
| Doctor workload | `GET /api/admin/reports/doctors` | Appointments, completion rate |
| Patient trends | `GET /api/admin/reports/patients` | Registration over time |

---

### 6.3 Security Hardening

- JWT expiry and refresh token rotation with secure HttpOnly cookies
- Rate limiting on login endpoint — lock after N failed attempts
- Input validation and sanitization on all endpoints
- SQL injection prevention via parameterized queries throughout
- CORS policy — whitelist only frontend origins
- HTTPS enforcement with HSTS headers
- Sensitive data masking in logs (passwords, tokens, patient data)
- Role-based route guards on all API endpoints — validate JWT role claims

---

### 6.4 Notifications & Communication

- Email on appointment booking confirmation (patient and doctor)
- Email on appointment cancellation
- Subscription expiry warning email to hospital admin (7 days, 3 days, 1 day before)
- In-app notification bell for doctors — new patient checked in
- SMS integration stub for appointment reminders (pluggable provider)

---

### 6.5 Performance & Infrastructure

- Database indexing on `tenant_id`, appointment date, patient search fields
- Medicine search backed by a search index (Elasticsearch or PostgreSQL full-text search)
- API response caching for stable data (departments, medicine catalog)
- Pagination enforced on all list endpoints — no unbounded queries
- Health check endpoint for load balancer probes
- Structured logging with correlation IDs per request
- Error monitoring integration (Sentry or equivalent)

---

### 6.6 Frontend Screens — Phase 5

| Screen | Role | Key Elements |
|--------|------|--------------|
| Super Admin Reports | Super Admin | Platform KPI dashboard with charts |
| Hospital Reports | Admin | Date range pickers, doctor filters, export to CSV |
| Quota Usage Widget | Admin | Progress bars for users, patients, storage, API calls |
| Notification Center | Doctor / Admin | Bell icon with unread count, notification list |
| System Health | Super Admin | API uptime, error rate, response time trends |

---

### 6.7 Phase 5 Acceptance Criteria

- [ ] All plan limits enforced at API layer — UI bypass attempts still blocked
- [ ] Login brute-force lock triggers after 5 failed attempts within 10 minutes
- [ ] List endpoints never return more than 100 records without pagination
- [ ] Medicine search p95 latency under 100ms under load
- [ ] Zero cross-tenant data leaks verified by automated test suite
- [ ] Subscription expiry email sent correctly at 7, 3, and 1 day before expiry

---

## 7. Complete Feature Checklist

Use this as your sprint planning and QA sign-off checklist.

### Infrastructure & Auth
- [ ] Multi-tenant architecture with automatic `tenant_id` scoping
- [ ] JWT authentication with embedded roles and tenant context
- [ ] Token refresh and secure session management
- [ ] Login blocking for suspended and expired tenants
- [ ] Password encryption (bcrypt)
- [ ] Role-based route guards on all APIs

### Hospital (Tenant) Management
- [ ] Hospital registration by Super Admin
- [ ] Hospital listing with filters and pagination
- [ ] Subscription plan assignment and updates
- [ ] Hospital suspend and activate
- [ ] Reset tenant admin password
- [ ] Quota usage monitoring

### User Management
- [ ] Create, update, delete users with employee IDs
- [ ] Activate and deactivate users
- [ ] Multi-role assignment per user
- [ ] Plan-based user limit enforcement

### Access Control
- [ ] Page registry with all system routes
- [ ] Role-level page access configuration
- [ ] User-level page grant and deny overrides
- [ ] Effective access computation (`GET /api/me/access`)
- [ ] Access change audit log

### Patient Management
- [ ] Patient registration with full profile
- [ ] Patient search by name, phone, ID
- [ ] Patient profile view and update
- [ ] Plan-based patient limit enforcement
- [ ] Appointment history per patient

### Appointment Management
- [ ] Scheduled appointment booking with 30-minute slots
- [ ] Slot availability check (09:00–17:00)
- [ ] Double-booking prevention
- [ ] Walk-in appointment with auto-token generation
- [ ] Appointment list with filters
- [ ] Status lifecycle: BOOKED → CHECKED_IN → IN_PROGRESS → COMPLETED / CANCELLED
- [ ] Department listing for booking workflow

### Clinical Workflow
- [ ] Doctor today's queue (scheduled + walk-in merged)
- [ ] Diagnosis creation (one per appointment)
- [ ] Diagnosis update while appointment is open
- [ ] Diagnosis severity levels (MILD / MODERATE / SEVERE / CRITICAL)
- [ ] Batch prescription creation
- [ ] Prescription view per diagnosis
- [ ] Medicine autocomplete search (min 2 chars)
- [ ] Central medicine catalog management

### Reporting
- [ ] Platform summary report (Super Admin)
- [ ] Appointment volume reports
- [ ] Doctor workload report
- [ ] Patient registration trends
- [ ] Subscription and revenue report

### Platform Hardening
- [ ] API rate limiting per plan
- [ ] Input validation and SQL injection prevention
- [ ] CORS, HTTPS, HSTS security headers
- [ ] Brute-force login protection
- [ ] Database indexing for performance
- [ ] Medicine search index
- [ ] Structured logging with correlation IDs
- [ ] Error monitoring integration
- [ ] Subscription expiry email notifications
- [ ] Appointment confirmation and cancellation emails

---

## 8. Timeline Summary

| Phase | Name | Duration | Key Deliverables | Dependencies |
|-------|------|----------|------------------|--------------|
| 1 | Foundation & Auth | 3 weeks | Multi-tenancy, JWT, Super Admin, Hospital CRUD | None |
| 2 | User & Access Mgmt | 4 weeks | Users, Roles, Page Access, Audit Log | Phase 1 |
| 3 | Patient & Appointments | 5 weeks | Registration, Scheduled, Walk-in, Departments | Phase 2 |
| 4 | Clinical Workflow | 5 weeks | Doctor Queue, Diagnosis, Prescriptions, Medicines | Phase 3 |
| 5 | Hardening & Reporting | 4+ weeks | Quotas, Reports, Security, Notifications, Perf | Phase 4 |

**Total estimated timeline: 21+ weeks.**

> Teams with parallel frontend/backend development can compress Phase 3 and 4 by running them concurrently once the API contracts are defined.

---

*CuraMatrix HSM — Phase-wise Feature Build Guide | v1.0 | Internal Use Only*