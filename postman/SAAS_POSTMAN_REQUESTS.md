# CuraMatrix HSM SaaS - Complete Postman Requests

## 📦 Collection Overview

This document contains all API requests for the Multi-Tenant SaaS version of CuraMatrix HSM.

---

## 🔧 Environment Variables

Add these to your Postman environment:

```json
{
  "baseUrl": "http://localhost:8080",
  "token": "",
  "userId": "",
  "userRole": "",
  "tenantId": "",
  "tenantKey": "",
  "hospitalName": "",
  "patientId": "",
  "appointmentId": "",
  "diagnosisId": "",
  "doctorId": "1"
}
```

---

## 0️⃣ Super Admin - Tenant Management

### 0.1 Login as Super Admin
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "superadmin@curamatrix.com",
  "password": "admin123",
  "tenantKey": "default-hospital"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("token", jsonData.token);
    pm.environment.set("userId", jsonData.userId);
    pm.environment.set("userRole", jsonData.role);
    pm.environment.set("tenantId", jsonData.tenantId);
    pm.environment.set("tenantKey", jsonData.tenantKey);
    pm.environment.set("hospitalName", jsonData.hospitalName);
}
```

---

### 0.2 Register New Hospital (Tenant)
```http
POST {{baseUrl}}/api/super-admin/tenants
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PREMIUM",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2027-03-11",
  "contactEmail": "contact@apollo-mumbai.com",
  "contactPhone": "02212345678",
  "address": "123 Marine Drive, Mumbai, Maharashtra 400001",
  "logo": "https://example.com/apollo-logo.png",
  "adminFullName": "Apollo Admin",
  "adminEmail": "admin@apollo-mumbai.com",
  "adminPassword": "admin123",
  "adminPhone": "9876543200"
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    var jsonData = pm.response.json();
    pm.environment.set("newTenantId", jsonData.id);
    pm.environment.set("newTenantKey", jsonData.tenantKey);
}
```

---

### 0.3 Register Another Hospital - Fortis Delhi
```http
POST {{baseUrl}}/api/super-admin/tenants
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "fortis-delhi",
  "hospitalName": "Fortis Hospital Delhi",
  "subscriptionPlan": "STANDARD",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2027-03-11",
  "contactEmail": "contact@fortis-delhi.com",
  "contactPhone": "01112345678",
  "address": "456 Nehru Place, New Delhi 110019",
  "adminFullName": "Fortis Admin",
  "adminEmail": "admin@fortis-delhi.com",
  "adminPassword": "admin123",
  "adminPhone": "9876543201"
}
```

---

### 0.4 Register Basic Plan Hospital
```http
POST {{baseUrl}}/api/super-admin/tenants
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "city-clinic-pune",
  "hospitalName": "City Clinic Pune",
  "subscriptionPlan": "BASIC",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2026-06-11",
  "contactEmail": "contact@cityclinic-pune.com",
  "contactPhone": "02012345678",
  "address": "789 FC Road, Pune, Maharashtra 411004",
  "adminFullName": "Clinic Admin",
  "adminEmail": "admin@cityclinic-pune.com",
  "adminPassword": "admin123",
  "adminPhone": "9876543202"
}
```

---

### 0.5 Get All Tenants
```http
GET {{baseUrl}}/api/super-admin/tenants
Authorization: Bearer {{token}}
```

---

### 0.6 Get Tenant by ID
```http
GET {{baseUrl}}/api/super-admin/tenants/{{newTenantId}}
Authorization: Bearer {{token}}
```

---

### 0.7 Update Tenant
```http
PUT {{baseUrl}}/api/super-admin/tenants/{{newTenantId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai - Updated",
  "subscriptionPlan": "PREMIUM",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2028-03-11",
  "contactEmail": "contact@apollo-mumbai.com",
  "contactPhone": "02212345678",
  "address": "123 Marine Drive, Mumbai, Maharashtra 400001",
  "adminFullName": "Apollo Admin",
  "adminEmail": "admin@apollo-mumbai.com",
  "adminPassword": "admin123"
}
```

---

### 0.8 Get Tenant Usage Statistics
```http
GET {{baseUrl}}/api/super-admin/tenants/{{newTenantId}}/stats
Authorization: Bearer {{token}}
```

---

### 0.9 Suspend Tenant
```http
PUT {{baseUrl}}/api/super-admin/tenants/{{newTenantId}}/suspend
Authorization: Bearer {{token}}
```

---

### 0.10 Activate Tenant
```http
PUT {{baseUrl}}/api/super-admin/tenants/{{newTenantId}}/activate
Authorization: Bearer {{token}}
```

---

## 1️⃣ Authentication (Multi-Tenant)

### 1.1 Login - Default Hospital Admin
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "admin@curamatrix.com",
  "password": "admin123",
  "tenantKey": "default-hospital"
}
```

---

### 1.2 Login - Apollo Mumbai Admin
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "admin@apollo-mumbai.com",
  "password": "admin123",
  "tenantKey": "apollo-mumbai"
}
```

---

### 1.3 Login - Fortis Delhi Admin
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "admin@fortis-delhi.com",
  "password": "admin123",
  "tenantKey": "fortis-delhi"
}
```

---

### 1.4 Login - Default Hospital Doctor
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "doctor@curamatrix.com",
  "password": "doctor123",
  "tenantKey": "default-hospital"
}
```

---

### 1.5 Login - Default Hospital Receptionist
```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "reception@curamatrix.com",
  "password": "reception123",
  "tenantKey": "default-hospital"
}
```

---

## 2️⃣ Admin - User Management (Tenant Level)

### 2.1 Create Doctor User
```http
POST {{baseUrl}}/api/admin/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "email": "dr-sharma@apollo-mumbai.com",
  "password": "doctor123",
  "fullName": "Dr. Rajesh Sharma",
  "phone": "9876543212",
  "role": "ROLE_DOCTOR",
  "departmentId": 1,
  "specialization": "Cardiology",
  "licenseNumber": "MCI-CARD-001",
  "qualification": "MBBS, MD (Cardiology)",
  "experienceYears": 15,
  "consultationFee": 1500.00
}
```

---

### 2.2 Create Receptionist User
```http
POST {{baseUrl}}/api/admin/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "email": "reception2@apollo-mumbai.com",
  "password": "reception123",
  "fullName": "Priya Patel",
  "phone": "9876543213",
  "role": "ROLE_RECEPTIONIST",
  "employeeId": "REC-APO-002",
  "shift": "AFTERNOON"
}
```

---

### 2.3 Create Another Doctor
```http
POST {{baseUrl}}/api/admin/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "email": "dr-mehta@apollo-mumbai.com",
  "password": "doctor123",
  "fullName": "Dr. Anjali Mehta",
  "phone": "9876543214",
  "role": "ROLE_DOCTOR",
  "departmentId": 4,
  "specialization": "Pediatrics",
  "licenseNumber": "MCI-PED-002",
  "qualification": "MBBS, MD (Pediatrics)",
  "experienceYears": 10,
  "consultationFee": 1000.00
}
```

---

### 2.4 Get All Users (Tenant Specific)
```http
GET {{baseUrl}}/api/admin/users
Authorization: Bearer {{token}}
```

---

### 2.5 Get User by ID
```http
GET {{baseUrl}}/api/admin/users/{{userId}}
Authorization: Bearer {{token}}
```

---

### 2.6 Deactivate User
```http
PUT {{baseUrl}}/api/admin/users/{{userId}}/deactivate
Authorization: Bearer {{token}}
```

---

### 2.7 Activate User
```http
PUT {{baseUrl}}/api/admin/users/{{userId}}/activate
Authorization: Bearer {{token}}
```

---

## 3️⃣ Patients (Tenant Isolated)

### 3.1 Register Patient
```http
POST {{baseUrl}}/api/patients
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit@email.com",
  "address": "123 MG Road, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "9876543211",
  "allergies": "Penicillin",
  "medicalHistory": "Diabetes Type 2"
}
```

---

### 3.2 Register Another Patient
```http
POST {{baseUrl}}/api/patients
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "firstName": "Sunita",
  "lastName": "Devi",
  "dateOfBirth": "1985-08-20",
  "gender": "FEMALE",
  "phone": "9876543220",
  "email": "sunita@email.com",
  "address": "456 Linking Road, Mumbai",
  "bloodGroup": "O_POSITIVE",
  "emergencyContactName": "Ramesh Devi",
  "emergencyContactPhone": "9876543221",
  "allergies": "None",
  "medicalHistory": "Hypertension"
}
```

---

### 3.3 Search Patients
```http
GET {{baseUrl}}/api/patients?search=amit&page=0&size=20
Authorization: Bearer {{token}}
```

---

### 3.4 Get Patient by ID
```http
GET {{baseUrl}}/api/patients/{{patientId}}
Authorization: Bearer {{token}}
```

---

### 3.5 Update Patient
```http
PUT {{baseUrl}}/api/patients/{{patientId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit.updated@email.com",
  "address": "789 New Address, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "9876543211",
  "allergies": "Penicillin, Sulfa drugs",
  "medicalHistory": "Diabetes Type 2, Hypertension"
}
```

---

## 4️⃣ Appointments (Tenant Isolated)

### 4.1 Book Scheduled Appointment
```http
POST {{baseUrl}}/api/appointments
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "patientId": 1,
  "doctorId": 1,
  "appointmentDate": "2026-03-20",
  "appointmentTime": "10:30",
  "notes": "Follow-up for diabetes"
}
```

---

### 4.2 Create Walk-in Appointment
```http
POST {{baseUrl}}/api/appointments/walk-in
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "patientId": 1,
  "doctorId": 1,
  "notes": "Fever and headache"
}
```

---

### 4.3 Get Appointments (Filtered)
```http
GET {{baseUrl}}/api/appointments?date=2026-03-20&doctorId=1&status=BOOKED
Authorization: Bearer {{token}}
```

---

### 4.4 Get Available Slots
```http
GET {{baseUrl}}/api/appointments/doctor/1/slots?date=2026-03-20
Authorization: Bearer {{token}}
```

---

### 4.5 Get Doctor's Today Queue
```http
GET {{baseUrl}}/api/appointments/doctor/1/today
Authorization: Bearer {{token}}
```

---

### 4.6 Update Status - CHECKED_IN
```http
PUT {{baseUrl}}/api/appointments/{{appointmentId}}/status?status=CHECKED_IN
Authorization: Bearer {{token}}
```

---

### 4.7 Update Status - IN_PROGRESS
```http
PUT {{baseUrl}}/api/appointments/{{appointmentId}}/status?status=IN_PROGRESS
Authorization: Bearer {{token}}
```

---

### 4.8 Update Status - COMPLETED
```http
PUT {{baseUrl}}/api/appointments/{{appointmentId}}/status?status=COMPLETED
Authorization: Bearer {{token}}
```

---

## 5️⃣ Diagnosis & Prescriptions

### 5.1 Create Diagnosis
```http
POST {{baseUrl}}/api/diagnoses
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "appointmentId": 1,
  "symptoms": "High blood sugar, frequent urination, fatigue",
  "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
  "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes.",
  "severity": "MODERATE",
  "followUpDate": "2026-04-20"
}
```

---

### 5.2 Get Diagnosis by ID
```http
GET {{baseUrl}}/api/diagnoses/{{diagnosisId}}
Authorization: Bearer {{token}}
```

---

### 5.3 Get Diagnosis by Appointment
```http
GET {{baseUrl}}/api/diagnoses/appointment/{{appointmentId}}
Authorization: Bearer {{token}}
```

---

### 5.4 Update Diagnosis
```http
PUT {{baseUrl}}/api/diagnoses/{{diagnosisId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "appointmentId": 1,
  "symptoms": "High blood sugar, frequent urination, fatigue, blurred vision",
  "diagnosis": "Uncontrolled Type 2 Diabetes with complications",
  "clinicalNotes": "HbA1c: 8.5%. Immediate medication adjustment needed.",
  "severity": "SEVERE",
  "followUpDate": "2026-04-10"
}
```

---

### 5.5 Add Prescriptions (Batch)
```http
POST {{baseUrl}}/api/prescriptions
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "diagnosisId": 1,
  "prescriptions": [
    {
      "medicineId": 10,
      "dosage": "500mg",
      "frequency": "Twice daily",
      "durationDays": 30,
      "instructions": "Take after meals"
    },
    {
      "medicineId": 12,
      "dosage": "1mg",
      "frequency": "Once daily (morning)",
      "durationDays": 30,
      "instructions": "Take before breakfast"
    }
  ]
}
```

---

### 5.6 Get Prescriptions by Diagnosis
```http
GET {{baseUrl}}/api/prescriptions/diagnosis/{{diagnosisId}}
Authorization: Bearer {{token}}
```

---

## 6️⃣ Medicine Search

### 6.1 Search - Paracetamol
```http
GET {{baseUrl}}/api/medicines/search?query=parac
Authorization: Bearer {{token}}
```

---

### 6.2 Search - Metformin
```http
GET {{baseUrl}}/api/medicines/search?query=metf
Authorization: Bearer {{token}}
```

---

### 6.3 Search - Amoxicillin
```http
GET {{baseUrl}}/api/medicines/search?query=amox
Authorization: Bearer {{token}}
```

---

### 6.4 Search - Generic Name
```http
GET {{baseUrl}}/api/medicines/search?query=acetamin
Authorization: Bearer {{token}}
```

---

### 6.5 Search - Brand Name
```http
GET {{baseUrl}}/api/medicines/search?query=crocin
Authorization: Bearer {{token}}
```

---

## 7️⃣ Departments

### 7.1 Get All Departments
```http
GET {{baseUrl}}/api/departments
Authorization: Bearer {{token}}
```

---

### 7.2 Get Department by ID
```http
GET {{baseUrl}}/api/departments/1
Authorization: Bearer {{token}}
```

---

## 🧪 Testing Multi-Tenant Isolation

### Test 1: Cross-Tenant Data Access (Should Fail)
1. Login to Apollo Mumbai
2. Note a patient ID
3. Login to Fortis Delhi
4. Try to access Apollo's patient - should get 404

### Test 2: Quota Enforcement
1. Login to Basic Plan hospital (City Clinic)
2. Try to create 11th user - should fail with quota exceeded

### Test 3: Subscription Expiry
1. Create tenant with past expiry date
2. Try to login - should fail with subscription expired

---

## 📊 Response Examples

### Successful Login Response
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "userId": 5,
  "fullName": "Apollo Admin",
  "role": "ROLE_ADMIN",
  "expiresIn": 86400000,
  "tenantId": 2,
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PREMIUM",
  "subscriptionExpiry": "2027-03-11"
}
```

### Tenant Registration Response
```json
{
  "id": 2,
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PREMIUM",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2027-03-11",
  "isActive": true,
  "maxUsers": -1,
  "maxPatients": -1,
  "contactEmail": "contact@apollo-mumbai.com",
  "contactPhone": "02212345678",
  "address": "123 Marine Drive, Mumbai",
  "logo": "https://example.com/apollo-logo.png",
  "createdAt": "2026-03-11T10:30:00",
  "currentUsers": 1,
  "currentPatients": 0
}
```

---

## 🎯 Complete Workflow Example

### Scenario: Register Hospital → Add Staff → Register Patient → Complete Consultation

```bash
# 1. Login as Super Admin
POST /api/auth/login
{tenantKey: "default-hospital", email: "superadmin@...", password: "..."}

# 2. Register New Hospital
POST /api/super-admin/tenants
{tenantKey: "apollo-mumbai", ...}

# 3. Login as Hospital Admin
POST /api/auth/login
{tenantKey: "apollo-mumbai", email: "admin@apollo-mumbai.com", ...}

# 4. Create Doctor
POST /api/admin/users
{role: "ROLE_DOCTOR", ...}

# 5. Create Receptionist
POST /api/admin/users
{role: "ROLE_RECEPTIONIST", ...}

# 6. Login as Receptionist
POST /api/auth/login
{tenantKey: "apollo-mumbai", email: "reception@...", ...}

# 7. Register Patient
POST /api/patients
{firstName: "Amit", ...}

# 8. Book Appointment
POST /api/appointments
{patientId: 1, doctorId: 1, ...}

# 9. Login as Doctor
POST /api/auth/login
{tenantKey: "apollo-mumbai", email: "doctor@...", ...}

# 10. View Queue
GET /api/appointments/doctor/1/today

# 11. Start Consultation
PUT /api/appointments/1/status?status=IN_PROGRESS

# 12. Create Diagnosis
POST /api/diagnoses
{appointmentId: 1, ...}

# 13. Add Prescriptions
POST /api/prescriptions
{diagnosisId: 1, prescriptions: [...]}

# 14. Complete
PUT /api/appointments/1/status?status=COMPLETED
```

---

## 🔒 Security Notes

1. **Always include tenantKey** in login requests
2. **Token includes tenant context** - automatically filtered
3. **Cross-tenant access blocked** - returns 404
4. **Quota enforcement** - prevents resource abuse
5. **Subscription validation** - checks expiry on every request

---

## 📞 Support

For issues with Postman collection:
- Check environment variables are set
- Verify token is saved after login
- Ensure tenantKey matches your hospital
- Review SAAS_ARCHITECTURE.md for details
