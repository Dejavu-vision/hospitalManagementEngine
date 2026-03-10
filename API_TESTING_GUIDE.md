# API Testing Guide - CuraMatrix HSM

Complete guide for testing the Hospital Management System APIs using Swagger UI and Postman.

---

## 🚀 Quick Start

### Option 1: Swagger UI (Recommended for Quick Testing)

1. **Start the application**
   ```bash
   ./gradlew bootRun
   ```

2. **Open Swagger UI**
   ```
   http://localhost:8080/swagger-ui.html
   ```

3. **Login and Authorize**
   - Expand **"1. Authentication"** section
   - Click **"POST /api/auth/login"**
   - Click **"Try it out"**
   - Select an example (Admin/Doctor/Receptionist)
   - Click **"Execute"**
   - Copy the `token` from the response
   - Click **"Authorize"** button (🔒) at the top
   - Paste the token (without "Bearer" prefix)
   - Click **"Authorize"** then **"Close"**

4. **Test APIs**
   - All endpoints are now authenticated
   - Navigate through sections 2-8
   - Try different endpoints based on your role

### Option 2: Postman (Recommended for Comprehensive Testing)

1. **Import Collection**
   - Open Postman
   - Click **Import**
   - Select `postman/CuraMatrix_HSM.postman_collection.json`
   - Click **Import**

2. **Import Environment**
   - Click **Import** again
   - Select `postman/CuraMatrix_HSM.postman_environment.json`
   - Click **Import**

3. **Select Environment**
   - Click environment dropdown (top right)
   - Select **"CuraMatrix HSM - Local"**

4. **Login First**
   - Open **"1. Authentication"** folder
   - Run **"Login - Admin"** (or Doctor/Receptionist)
   - Token is automatically saved to environment

5. **Test Other Endpoints**
   - All requests now use the saved token automatically
   - IDs are auto-saved after creation (patientId, appointmentId, etc.)

---

## 📋 Testing Workflows

### Workflow 1: Complete Patient Journey (Receptionist → Doctor)

#### Step 1: Login as Receptionist
```bash
POST /api/auth/login
{
  "email": "reception@curamatrix.com",
  "password": "reception123"
}
```
**Save the token!**

#### Step 2: Register a New Patient
```bash
POST /api/patients
{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit@email.com",
  "address": "123 MG Road, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "allergies": "Penicillin",
  "medicalHistory": "Diabetes Type 2"
}
```
**Save the patientId from response!**

#### Step 3: Check Available Slots
```bash
GET /api/appointments/doctor/1/slots?date=2026-03-15
```

#### Step 4: Book Appointment
```bash
POST /api/appointments
{
  "patientId": 1,
  "doctorId": 1,
  "appointmentDate": "2026-03-15",
  "appointmentTime": "10:30",
  "notes": "Follow-up for diabetes"
}
```
**Save the appointmentId!**

#### Step 5: Patient Arrives - Check In
```bash
PUT /api/appointments/1/status?status=CHECKED_IN
```

#### Step 6: Login as Doctor
```bash
POST /api/auth/login
{
  "email": "doctor@curamatrix.com",
  "password": "doctor123"
}
```
**Save the new token!**

#### Step 7: View Today's Queue
```bash
GET /api/appointments/doctor/1/today
```

#### Step 8: Start Consultation
```bash
PUT /api/appointments/1/status?status=IN_PROGRESS
```

#### Step 9: Create Diagnosis
```bash
POST /api/diagnoses
{
  "appointmentId": 1,
  "symptoms": "High blood sugar, frequent urination, fatigue",
  "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
  "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes.",
  "severity": "MODERATE",
  "followUpDate": "2026-04-15"
}
```
**Save the diagnosisId!**

#### Step 10: Search for Medicines
```bash
GET /api/medicines/search?query=metf
```

#### Step 11: Add Prescriptions
```bash
POST /api/prescriptions
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
      "frequency": "Once daily",
      "durationDays": 30,
      "instructions": "Take before breakfast"
    }
  ]
}
```

#### Step 12: Complete Consultation
```bash
PUT /api/appointments/1/status?status=COMPLETED
```

---

### Workflow 2: Walk-in Patient (Receptionist)

#### Step 1: Login as Receptionist
```bash
POST /api/auth/login
{
  "email": "reception@curamatrix.com",
  "password": "reception123"
}
```

#### Step 2: Create Walk-in Appointment
```bash
POST /api/appointments/walk-in
{
  "patientId": 1,
  "doctorId": 1,
  "notes": "Fever and headache"
}
```
**Note the tokenNumber in response - this is the queue number!**

#### Step 3: View All Today's Appointments
```bash
GET /api/appointments?date=2026-03-11&doctorId=1
```

---

### Workflow 3: User Management (Admin)

#### Step 1: Login as Admin
```bash
POST /api/auth/login
{
  "email": "admin@curamatrix.com",
  "password": "admin123"
}
```

#### Step 2: View All Departments
```bash
GET /api/departments
```

#### Step 3: Create a New Doctor
```bash
POST /api/admin/users
{
  "email": "dr-prathmesh@curamatrix.com",
  "password": "doctor123",
  "fullName": "Dr. Prathmesh Sharma",
  "phone": "9876543212",
  "role": "ROLE_DOCTOR",
  "departmentId": 1,
  "specialization": "General Medicine",
  "licenseNumber": "MCI-GM-002",
  "qualification": "MBBS, MD",
  "experienceYears": 8,
  "consultationFee": 600.00
}
```

#### Step 4: Create a New Receptionist
```bash
POST /api/admin/users
{
  "email": "receptionist2@curamatrix.com",
  "password": "reception123",
  "fullName": "Priya Patel",
  "phone": "9876543213",
  "role": "ROLE_RECEPTIONIST",
  "employeeId": "REC-002",
  "shift": "AFTERNOON"
}
```

#### Step 5: View All Users
```bash
GET /api/admin/users
```

#### Step 6: Deactivate a User
```bash
PUT /api/admin/users/2/deactivate
```

---

## 🔍 Testing Scenarios

### Scenario 1: Search Patients
```bash
# Search by name
GET /api/patients?search=amit

# Search by phone
GET /api/patients?search=9876543210

# Pagination
GET /api/patients?page=0&size=10&sortBy=registeredAt&sortDir=desc
```

### Scenario 2: Filter Appointments
```bash
# By date
GET /api/appointments?date=2026-03-15

# By doctor
GET /api/appointments?doctorId=1

# By status
GET /api/appointments?status=BOOKED

# By type
GET /api/appointments?type=WALK_IN

# Combined filters
GET /api/appointments?date=2026-03-15&doctorId=1&status=BOOKED
```

### Scenario 3: Medicine Search
```bash
# By medicine name
GET /api/medicines/search?query=parac

# By generic name
GET /api/medicines/search?query=acetamin

# By brand
GET /api/medicines/search?query=crocin

# Partial match
GET /api/medicines/search?query=met
```

### Scenario 4: Update Patient Information
```bash
PUT /api/patients/1
{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit.updated@email.com",
  "address": "456 New Address, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "allergies": "Penicillin, Sulfa drugs",
  "medicalHistory": "Diabetes Type 2, Hypertension"
}
```

### Scenario 5: Update Diagnosis
```bash
PUT /api/diagnoses/1
{
  "appointmentId": 1,
  "symptoms": "High blood sugar, frequent urination, fatigue, blurred vision",
  "diagnosis": "Uncontrolled Type 2 Diabetes with complications",
  "clinicalNotes": "HbA1c: 8.5%. Immediate medication adjustment needed.",
  "severity": "SEVERE",
  "followUpDate": "2026-04-01"
}
```

---

## 🎯 API Endpoints Summary

### 1. Authentication (Public)
- `POST /api/auth/login` - Login and get JWT token

### 2. Admin - User Management (ADMIN only)
- `POST /api/admin/users` - Create new user
- `GET /api/admin/users` - List all users
- `GET /api/admin/users/{id}` - Get user details
- `PUT /api/admin/users/{id}/deactivate` - Deactivate user
- `PUT /api/admin/users/{id}/activate` - Activate user

### 3. Patients (RECEPTIONIST, DOCTOR)
- `POST /api/patients` - Register patient (RECEPTIONIST)
- `GET /api/patients` - Search patients
- `GET /api/patients/{id}` - Get patient details
- `PUT /api/patients/{id}` - Update patient (RECEPTIONIST)

### 4. Appointments (RECEPTIONIST, DOCTOR)
- `POST /api/appointments` - Book scheduled appointment (RECEPTIONIST)
- `POST /api/appointments/walk-in` - Create walk-in (RECEPTIONIST)
- `GET /api/appointments` - List appointments (RECEPTIONIST)
- `GET /api/appointments/doctor/{id}/slots` - Get available slots (RECEPTIONIST)
- `GET /api/appointments/doctor/{id}/today` - Get today's queue (DOCTOR)
- `PUT /api/appointments/{id}/status` - Update status

### 5. Diagnosis (DOCTOR only)
- `POST /api/diagnoses` - Create diagnosis
- `GET /api/diagnoses/{id}` - Get diagnosis
- `GET /api/diagnoses/appointment/{id}` - Get by appointment
- `PUT /api/diagnoses/{id}` - Update diagnosis

### 6. Prescriptions (DOCTOR only)
- `POST /api/prescriptions` - Add prescriptions (batch)
- `GET /api/prescriptions/diagnosis/{id}` - Get by diagnosis

### 7. Medicine Search (DOCTOR, ADMIN)
- `GET /api/medicines/search?query={text}` - Search medicines

### 8. Departments (ADMIN, RECEPTIONIST)
- `GET /api/departments` - List all departments
- `GET /api/departments/{id}` - Get department details

---

## 🐛 Common Issues & Solutions

### Issue 1: 401 Unauthorized
**Solution:** 
- Make sure you've logged in first
- Check that token is set in Authorization header
- Token might be expired - login again

### Issue 2: 403 Forbidden
**Solution:**
- You're using the wrong role
- Login with correct user (Admin/Doctor/Receptionist)

### Issue 3: 400 Bad Request - Validation Error
**Solution:**
- Check required fields in request body
- Verify data types (dates, enums, etc.)
- Read error message for specific field errors

### Issue 4: 409 Conflict - Slot Already Booked
**Solution:**
- Check available slots first: `GET /api/appointments/doctor/{id}/slots`
- Choose a different time slot

### Issue 5: 404 Not Found
**Solution:**
- Verify the ID exists in database
- Check if you're using correct endpoint URL

---

## 📊 Test Data

### Default Users
| Email | Password | Role |
|-------|----------|------|
| admin@curamatrix.com | admin123 | ADMIN |
| doctor@curamatrix.com | doctor123 | DOCTOR |
| reception@curamatrix.com | reception123 | RECEPTIONIST |

### Departments (IDs 1-10)
1. General Medicine
2. Cardiology
3. Orthopedics
4. Pediatrics
5. Dermatology
6. ENT
7. Ophthalmology
8. Gynecology
9. Neurology
10. Dentistry

### Sample Medicine IDs
- 1-3: Paracetamol variants
- 4-5: Amoxicillin
- 6: Azithromycin
- 10-11: Metformin
- 12-13: Glimepiride
- 14: Amlodipine

### Enums Reference

**Gender:** `MALE`, `FEMALE`, `OTHER`

**Blood Group:** `A_POSITIVE`, `A_NEGATIVE`, `B_POSITIVE`, `B_NEGATIVE`, `O_POSITIVE`, `O_NEGATIVE`, `AB_POSITIVE`, `AB_NEGATIVE`

**Appointment Type:** `SCHEDULED`, `WALK_IN`

**Appointment Status:** `BOOKED`, `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`

**Severity:** `MILD`, `MODERATE`, `SEVERE`, `CRITICAL`

**Shift:** `MORNING`, `AFTERNOON`, `NIGHT`

**Role:** `ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_RECEPTIONIST`

---

## 🎓 Best Practices

1. **Always login first** before testing other endpoints
2. **Save IDs** from responses for subsequent requests
3. **Use environment variables** in Postman for dynamic values
4. **Test role-based access** by switching between users
5. **Check response status codes** to understand errors
6. **Read error messages** for validation failures
7. **Use Swagger UI** for quick API exploration
8. **Use Postman** for comprehensive workflow testing

---

## 📞 Support

For issues or questions:
- Email: support@curamatrix.com
- Check logs: `./gradlew bootRun` output
- Review documentation: `docs/` folder
