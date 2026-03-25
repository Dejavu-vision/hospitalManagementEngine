# Backend API Guide

## Purpose of this guide

This guide explains how the backend works and lists all APIs in a simple, implementation-accurate format.

Tech stack:
- Java 17
- Spring Boot
- Spring Security + JWT
- Spring Data JPA
- MySQL

---

## High-level architecture

```text
Request
  -> Security filter (JWT validation)
  -> Tenant interceptor (extract tenant claims)
  -> Controller
  -> Service
  -> Repository
  -> MySQL
```

Core idea:
- JWT contains `tenantId`, `tenantKey`, authorities, and page keys.
- `TenantContext` is set for each request and cleared after completion.
- Business and data queries are scoped by tenant.

---

## Security model

Public endpoints:
- `/api/auth/**`
- `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`
- `/actuator/health`, `/actuator/info`
- static frontend assets

Everything else requires authentication.

Authentication and authorization:
- Password login returns bearer token.
- Role checks via `@PreAuthorize`.
- Some APIs use page/permission authority checks (example: medicine search requires `MEDICINE_SEARCH`).

---

## Roles used in backend

- `ROLE_SUPER_ADMIN` - platform-wide control
- `ROLE_ADMIN` - tenant/hospital admin
- `ROLE_DOCTOR` - consultation operations
- `ROLE_RECEPTIONIST` - patient and appointment desk operations

---

## Multi-tenant behavior

- Tenant comes from JWT claims.
- `TenantInterceptor` stores tenant info in `TenantContext`.
- Tenant data is isolated by `tenant_id`.
- Login is blocked when tenant is suspended or subscription has expired.

---

## Subscription plans (from code)

| Plan | Price | Max Users | Max Patients | Storage | API Calls / Hour |
|---|---:|---:|---:|---:|---:|
| BASIC | 99 | 10 | 1,000 | 5 GB | 1,000 |
| STANDARD | 499 | 50 | 10,000 | 50 GB | 10,000 |
| PREMIUM | 1,999 | Unlimited | Unlimited | 500 GB | Unlimited |

Notes:
- Plan limits are copied to tenant (`maxUsers`, `maxPatients`) during tenant create/update.
- Unlimited is represented by `-1` in code.

---

## Domain modules

- Tenant management
- Authentication
- User and role management
- Access control (pages, role-pages, user overrides, denies)
- Patient management
- Appointment scheduling and walk-in queue
- Diagnosis and prescriptions
- Medicine search
- Department lookup

---

## Complete API list

### 1) Authentication
Base: `/api/auth`

- `POST /login`
  - Login using email + password
  - Returns token, role, tenant metadata, and page keys

### 2) Super Admin - Tenant Management
Base: `/api/super-admin/tenants` (requires `ROLE_SUPER_ADMIN`)

- `POST /`
- `GET /`
- `GET /{id}`
- `PUT /{id}`
- `PUT /{id}/suspend`
- `PUT /{id}/activate`
- `GET /{id}/stats`
- `PUT /{id}/admin/password`

### 3) Admin - User Management
Base: `/api/admin` (requires `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`)

- `POST /users`
- `GET /users`
- `GET /users/{id}`
- `PUT /users/{id}`
- `PUT /users/{id}/deactivate`
- `PUT /users/{id}/activate`
- `DELETE /users/{id}` (soft-delete behavior)
- `PUT /users/{id}/roles`
- `POST /users/{id}/roles/{roleName}`
- `DELETE /users/{id}/roles/{roleName}`
- `GET /users/{id}/audit`

### 4) Admin - Access Control
Base: `/api/admin/access` (requires `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`)

User access:
- `GET /users/{userId}`

User page overrides:
- `PUT /users/{userId}/pages`
- `DELETE /users/{userId}/pages`
- `POST /users/{userId}/pages/{pageKey}`
- `DELETE /users/{userId}/pages/{pageKey}`

User extra/denied pages:
- `GET /users/{userId}/pages/extra`
- `GET /users/{userId}/pages/granted`
- `GET /users/{userId}/pages/denied`
- `POST /users/{userId}/pages/{pageKey}/deny`
- `DELETE /users/{userId}/pages/{pageKey}/deny`

Role to pages mapping:
- `GET /roles/{roleName}/pages`
- `PUT /roles/{roleName}/pages`

Page catalog:
- `GET /pages`
- `POST /pages`
- `DELETE /pages/{pageKey}`

### 5) Current user access
Base: `/api/me`

- `GET /access`

### 6) Patients
Base: `/api/patients`

- `POST /` (Receptionist)
- `GET /` (Receptionist, Doctor) with search + pagination + sorting
- `GET /{id}` (Receptionist, Doctor)
- `PUT /{id}` (Receptionist)

### 7) Appointments
Base: `/api/appointments`

- `POST /` (book scheduled)
- `POST /walk-in`
- `GET /` (filter by date/doctor/patient/status/type)
- `GET /doctor/{doctorId}/slots`
- `GET /doctor/{doctorId}/today`
- `PUT /{id}/status`

### 8) Diagnoses
Base: `/api/diagnoses` (Doctor)

- `POST /`
- `GET /{id}`
- `GET /appointment/{appointmentId}`
- `PUT /{id}`

### 9) Prescriptions
Base: `/api/prescriptions` (Doctor)

- `POST /`
- `GET /diagnosis/{diagnosisId}`

### 10) Medicines
Base: `/api/medicines`

- `GET /search?query=`
  - Requires authority: `MEDICINE_SEARCH`
  - Query must be at least 2 characters

### 11) Departments
Base: `/api/departments`

- `GET /` (Admin, Receptionist)
- `GET /{id}` (Admin, Receptionist)

---

## Important business rules implemented

- A tenant key must be unique.
- Tenant registration auto-creates tenant admin user.
- Appointment slot conflict is blocked for scheduled bookings.
- Walk-in token is sequential per doctor per day.
- Slot duration is 30 minutes, generated between 09:00 and 17:00.
- Appointment statuses: `BOOKED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- Diagnosis severity values: `MILD`, `MODERATE`, `SEVERE`, `CRITICAL`.
- Login fails for suspended tenant or expired subscription.
- User delete endpoint performs soft delete behavior.

---

## Request/response behavior notes

- Most create APIs return `201 Created`.
- Update/read APIs return `200 OK`.
- Many action-style APIs return `204 No Content`.
- Validation and runtime errors are returned as standard HTTP error responses.

---

## Typical API flow (end-to-end)

1. `POST /api/auth/login` to get bearer token.
2. Call `GET /api/me/access` to fetch effective page access for UI.
3. Receptionist registers patient and books appointment.
4. Doctor loads queue and updates status.
5. Doctor creates diagnosis and prescriptions.
6. Admin/Super Admin manages users, tenant, and access policies.

---

## Backend extension checklist

When adding new APIs:
- add endpoint in controller with correct role/authority check
- implement logic in service
- enforce tenant scoping in data access
- validate request DTOs
- add OpenAPI annotations where needed
- test in Swagger and Postman
