# Frontend Integration Guide

Base URL: `http://43.204.168.146:8080` (production) | `http://localhost:8080` (local)

---

## Authentication

All requests (except login) require:
```
Authorization: Bearer <jwt_token>
```

### Login
**POST** `/api/auth/login`

Request:
```json
{
  "email": "string",
  "password": "string",
  "tenantKey": "string"
}
```

Response:
```json
{
  "token": "string",
  "tokenType": "Bearer",
  "userId": 1,
  "fullName": "string",
  "role": "ROLE_ADMIN | ROLE_DOCTOR | ROLE_RECEPTIONIST | ROLE_SUPER_ADMIN",
  "expiresIn": 86400000,
  "tenantId": 1,
  "tenantKey": "string",
  "hospitalName": "string",
  "subscriptionPlan": "string",
  "subscriptionExpiry": "2027-12-31"
}
```

Store `token` in localStorage/sessionStorage. Token expires in 24 hours.

---

## Role-Based Access

| Role | Access |
|---|---|
| `ROLE_SUPER_ADMIN` | Tenant management only |
| `ROLE_ADMIN` | User management within hospital |
| `ROLE_RECEPTIONIST` | Patients, appointments |
| `ROLE_DOCTOR` | Today's queue, diagnosis, prescriptions |

---

## Super Admin Endpoints

### Register Hospital
**POST** `/api/super-admin/tenants`
```json
{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PROFESSIONAL",
  "subscriptionStart": "2026-01-01",
  "subscriptionEnd": "2027-01-01",
  "contactEmail": "admin@apollo.com",
  "contactPhone": "9876543210",
  "address": "Mumbai, Maharashtra",
  "adminFullName": "Admin Name",
  "adminEmail": "admin@apollo.com",
  "adminPassword": "securepassword"
}
```

### Get All Hospitals
**GET** `/api/super-admin/tenants`

### Get Hospital by ID
**GET** `/api/super-admin/tenants/{id}`

### Update Hospital
**PUT** `/api/super-admin/tenants/{id}`

### Suspend / Activate Hospital
**PUT** `/api/super-admin/tenants/{id}/suspend`
**PUT** `/api/super-admin/tenants/{id}/activate`

### Hospital Stats
**GET** `/api/super-admin/tenants/{id}/stats`

---

## Admin Endpoints

### Create User
**POST** `/api/admin/users`

For Doctor:
```json
{
  "fullName": "Dr. John Smith",
  "email": "dr.john@hospital.com",
  "password": "doctor123",
  "phone": "9876543210",
  "role": "ROLE_DOCTOR",
  "specialization": "Cardiology",
  "licenseNumber": "MH-12345",
  "qualification": "MBBS, MD",
  "experienceYears": 10,
  "consultationFee": 500.00,
  "departmentId": 1
}
```

For Receptionist:
```json
{
  "fullName": "Jane Doe",
  "email": "jane@hospital.com",
  "password": "reception123",
  "phone": "9876543211",
  "role": "ROLE_RECEPTIONIST",
  "shift": "MORNING"
}
```

### Get All Users
**GET** `/api/admin/users`

### Get User by ID
**GET** `/api/admin/users/{id}`

### Activate / Deactivate User
**PUT** `/api/admin/users/{id}/activate`
**PUT** `/api/admin/users/{id}/deactivate`

---

## Department Endpoints

### Get All Departments
**GET** `/api/departments`

Response:
```json
[
  { "id": 1, "name": "Cardiology", "isActive": true },
  { "id": 2, "name": "Neurology", "isActive": true }
]
```

### Get Department by ID
**GET** `/api/departments/{id}`

---

## Patient Endpoints

### Register Patient
**POST** `/api/patients`
```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "rahul@email.com",
  "bloodGroup": "B_POSITIVE",
  "address": "123 Main Street, Mumbai",
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "9876543211",
  "allergies": "Penicillin",
  "medicalHistory": "Hypertension"
}
```

### Search / List Patients
**GET** `/api/patients?search=rahul&page=0&size=20&sortBy=registeredAt&sortDir=desc`

Query params:
- `search` — name or phone (optional)
- `page` — page number (default 0)
- `size` — page size (default 20)
- `sortBy` — field to sort (default `registeredAt`)
- `sortDir` — `asc` or `desc`

### Get Patient by ID
**GET** `/api/patients/{id}`

### Update Patient
**PUT** `/api/patients/{id}`

---

## Appointment Endpoints

### Book Appointment
**POST** `/api/appointments`
```json
{
  "patientId": 1,
  "doctorId": 1,
  "appointmentDate": "2026-03-20",
  "appointmentTime": "10:00:00",
  "notes": "Regular checkup"
}
```

### Walk-In Appointment
**POST** `/api/appointments/walk-in`
```json
{
  "patientId": 1,
  "doctorId": 1,
  "notes": "Walk-in patient"
}
```

### Get Appointments (with filters)
**GET** `/api/appointments?date=2026-03-20&doctorId=1&status=BOOKED&page=0&size=20`

Query params:
- `date` — filter by date (yyyy-MM-dd)
- `doctorId` — filter by doctor
- `patientId` — filter by patient
- `status` — `BOOKED | CHECKED_IN | IN_PROGRESS | COMPLETED | CANCELLED`
- `type` — `SCHEDULED | WALK_IN`

### Get Available Slots
**GET** `/api/appointments/doctor/{doctorId}/slots?date=2026-03-20`

### Get Doctor's Today Queue
**GET** `/api/appointments/doctor/{doctorId}/today`

### Update Appointment Status
**PUT** `/api/appointments/{id}/status?status=COMPLETED`

Status values: `BOOKED` → `CHECKED_IN` → `IN_PROGRESS` → `COMPLETED` | `CANCELLED`

---

## Diagnosis Endpoints

### Create Diagnosis
**POST** `/api/diagnoses`
```json
{
  "appointmentId": 1,
  "symptoms": "Chest tightness, fever",
  "diagnosis": "Acute Bronchitis",
  "clinicalNotes": "Patient advised rest",
  "severity": "MODERATE",
  "followUpDate": "2026-03-27"
}
```

### Get Diagnosis by ID
**GET** `/api/diagnoses/{id}`

### Get Diagnosis by Appointment
**GET** `/api/diagnoses/appointment/{appointmentId}`

### Update Diagnosis
**PUT** `/api/diagnoses/{id}`

---

## Prescription Endpoints

### Add Prescriptions
**POST** `/api/prescriptions`
```json
{
  "diagnosisId": 1,
  "prescriptions": [
    {
      "medicineId": 1,
      "dosage": "500mg",
      "frequency": "Twice daily",
      "durationDays": 5,
      "instructions": "Take after meals"
    }
  ]
}
```

### Get Prescriptions by Diagnosis
**GET** `/api/prescriptions/diagnosis/{diagnosisId}`

---

## Medicine Endpoints

### Search Medicines (autocomplete)
**GET** `/api/medicines/search?query=parac`

Min 2 characters. Returns top 10 matches.

Response:
```json
[
  { "id": 1, "name": "Paracetamol", "genericName": "Acetaminophen", "brand": "Crocin", "strength": "500mg", "form": "Tablet" }
]
```

---

## Health Endpoints (no auth required)

**GET** `/actuator/health` — DB status, disk space
**GET** `/actuator/info` — app version, git commit, build info

---

## Error Responses

```json
{ "status": 400, "error": "Bad Request", "message": "Validation failed" }
{ "status": 401, "error": "Unauthorized", "message": "Invalid or expired token" }
{ "status": 403, "error": "Forbidden", "message": "Access denied" }
{ "status": 404, "error": "Not Found", "message": "Resource not found" }
```

---

## CORS

Allowed origins (local dev): `http://localhost:3000`, `http://localhost:4200`

For production, update `SecurityConfig.java` with your frontend domain.
