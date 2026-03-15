# Product Guide

## What is CuraMatrix HSM?

CuraMatrix HSM is a multi-tenant SaaS Hospital Management System. Multiple hospitals can use the same platform, each with completely isolated data. One platform, many hospitals.

---

## User Roles

### Super Admin
- Platform owner
- Registers new hospitals on the platform
- Manages subscriptions and billing plans
- Can suspend or activate any hospital
- Does NOT have access to hospital data

### Admin (Hospital Admin)
- One per hospital
- Created automatically when a hospital is registered
- Creates and manages doctors and receptionists
- Cannot access patient data directly

### Doctor
- Created by Admin
- Views their daily appointment queue
- Creates diagnosis for each patient
- Prescribes medicines
- Cannot register patients or book appointments

### Receptionist
- Created by Admin
- Registers new patients
- Books appointments (scheduled or walk-in)
- Manages appointment status (check-in)
- Cannot create diagnosis or prescriptions

---

## Subscription Plans

| Plan | Max Users | Max Patients |
|---|---|---|
| BASIC | 10 | 1,000 |
| STANDARD | 50 | 10,000 |
| PROFESSIONAL | 100 | 50,000 |
| ENTERPRISE | Unlimited | Unlimited |

When a hospital exceeds its quota, new user/patient creation is blocked with a `QuotaExceededException`.

---

## Patient Journey

```
Patient Arrives
    │
    ▼
Receptionist registers patient (if new)
    │
    ▼
Receptionist books appointment (scheduled or walk-in)
    │
    ▼
Patient waits → Status: BOOKED
    │
    ▼
Receptionist checks patient in → Status: CHECKED_IN
    │
    ▼
Doctor calls patient → Status: IN_PROGRESS
    │
    ▼
Doctor creates diagnosis
    │
    ▼
Doctor searches and adds prescriptions
    │
    ▼
Appointment completed → Status: COMPLETED
    │
    ▼
Follow-up date set (optional)
```

---

## Appointment Types

**Scheduled** — Patient books in advance with a specific time slot.

**Walk-In** — Patient arrives without appointment. Gets an auto-assigned token number. Seen in queue order.

---

## Data Isolation

Each hospital's data is completely isolated. A doctor from Hospital A cannot see patients from Hospital B. This is enforced at the database level via `tenant_id` on every record.

---

## Business Rules

- A patient can have multiple appointments
- Each appointment has exactly one diagnosis
- Each diagnosis can have multiple prescriptions
- Medicines are shared across all hospitals (common catalogue)
- Medicine inventory is per hospital
- Appointment time slots are 30 minutes each
- A doctor cannot have two appointments at the same time
- Walk-in appointments get sequential token numbers per doctor per day
- Subscription expiry blocks all logins for that hospital
- Suspended hospitals cannot log in

---

## Severity Levels (Diagnosis)

| Level | Description |
|---|---|
| MILD | Minor condition, no immediate concern |
| MODERATE | Requires treatment, monitor closely |
| SEVERE | Urgent treatment required |
| CRITICAL | Immediate intervention needed |

---

## Key Screens (for Frontend Reference)

| Screen | Role | API Used |
|---|---|---|
| Login | All | POST /api/auth/login |
| Dashboard | All | GET /actuator/health |
| Hospital Management | Super Admin | GET/POST /api/super-admin/tenants |
| User Management | Admin | GET/POST /api/admin/users |
| Patient Registration | Receptionist | POST /api/patients |
| Patient Search | Receptionist/Doctor | GET /api/patients?search= |
| Book Appointment | Receptionist | POST /api/appointments |
| Today's Queue | Doctor | GET /api/appointments/doctor/{id}/today |
| Diagnosis Form | Doctor | POST /api/diagnoses |
| Prescription Form | Doctor | GET /api/medicines/search + POST /api/prescriptions |
| Appointment History | Receptionist/Doctor | GET /api/appointments |
