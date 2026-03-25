# API Flow Guide — End to End

This guide walks through the complete patient journey from login to prescription.

---

## Step 1 — Login

Every request requires a JWT token. Login first.

**POST** `/api/auth/login`

```json
{
  "email": "admin@hospital1.com",
  "password": "admin123",
  "tenantKey": "HOSPITAL_001"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "fullName": "Hospital Admin",
  "role": "ROLE_ADMIN",
  "tenantId": 1,
  "tenantKey": "HOSPITAL_001",
  "hospitalName": "City Hospital",
  "expiresIn": 86400000
}
```

Save the `token`. Add it to all subsequent requests:
```
Authorization: Bearer <token>
```

---

## Step 2 — Create a Doctor (Admin)

Login as Admin first, then:

**POST** `/api/admin/users`

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

Save the `id` from response as `doctorId`.

---

## Step 3 — Create a Receptionist (Admin)

**POST** `/api/admin/users`

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

---

## Step 4 — Register a Patient (Receptionist)

Login as Receptionist, then:

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
  "emergencyContactPhone": "9876543211"
}
```

Save the `id` from response as `patientId`.

---

## Step 5 — Check Available Slots (Receptionist)

**GET** `/api/appointments/doctor/{doctorId}/slots?date=2026-03-20`

Response shows available time slots for that doctor on that date.

---

## Step 6 — Book Appointment (Receptionist)

**POST** `/api/appointments`

```json
{
  "patientId": 1,
  "doctorId": 1,
  "appointmentDate": "2026-03-20",
  "appointmentTime": "10:00",
  "notes": "Regular checkup"
}
```

Save the `id` from response as `appointmentId`.

For walk-in patients:

**POST** `/api/appointments/walk-in`

```json
{
  "patientId": 1,
  "doctorId": 1,
  "notes": "Walk-in patient"
}
```

---

## Step 7 — Doctor Views Today's Queue (Doctor)

Login as Doctor, then:

**GET** `/api/appointments/doctor/{doctorId}/today`

Returns all appointments for today in token order.

---

## Step 8 — Update Appointment Status (Doctor/Receptionist)

When patient arrives:

**PUT** `/api/appointments/{appointmentId}/status?status=CHECKED_IN`

When doctor starts consultation:

**PUT** `/api/appointments/{appointmentId}/status?status=IN_PROGRESS`

---

## Step 9 — Create Diagnosis (Doctor)

**POST** `/api/diagnoses`

```json
{
  "appointmentId": 1,
  "symptoms": "Chest tightness, mild fever, fatigue",
  "diagnosis": "Acute Bronchitis",
  "clinicalNotes": "Patient advised rest",
  "severity": "MODERATE",
  "followUpDate": "2026-03-27"
}
```

Save the `id` from response as `diagnosisId`.

---

## Step 10 — Search Medicine (Doctor)

**GET** `/api/medicines/search?query=parac`

Returns matching medicines with their IDs. Save `medicineId`.

---

## Step 11 — Add Prescription (Doctor)

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
    },
    {
      "medicineId": 2,
      "dosage": "10mg",
      "frequency": "Once daily",
      "durationDays": 3,
      "instructions": "Take before sleep"
    }
  ]
}
```

---

## Step 12 — Complete Appointment (Doctor)

**PUT** `/api/appointments/{appointmentId}/status?status=COMPLETED`

---

## Full Flow Summary

```
Login (Admin)
  └── Create Doctor
  └── Create Receptionist

Login (Receptionist)
  └── Register Patient
  └── Check Available Slots
  └── Book Appointment

Login (Doctor)
  └── View Today's Queue
  └── Update Status → IN_PROGRESS
  └── Create Diagnosis
  └── Search Medicine
  └── Add Prescription
  └── Update Status → COMPLETED
```

---

## Appointment Status Flow

```
BOOKED → CHECKED_IN → IN_PROGRESS → COMPLETED
                                  → CANCELLED
```

---

## Severity Values
`MILD` | `MODERATE` | `SEVERE` | `CRITICAL`

## Gender Values
`MALE` | `FEMALE` | `OTHER`

## Blood Group Values
`A_POSITIVE` | `A_NEGATIVE` | `B_POSITIVE` | `B_NEGATIVE` | `O_POSITIVE` | `O_NEGATIVE` | `AB_POSITIVE` | `AB_NEGATIVE`
