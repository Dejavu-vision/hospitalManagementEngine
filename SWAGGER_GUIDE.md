# Swagger UI Guide - CuraMatrix HSM

Complete guide for using Swagger UI to test the Hospital Management System APIs.

---

## 🌐 Access Swagger UI

1. **Start the application**
   ```bash
   ./gradlew bootRun
   ```

2. **Open in browser**
   ```
   http://localhost:8080/swagger-ui.html
   ```

3. **Alternative URL**
   ```
   http://localhost:8080/swagger-ui/index.html
   ```

---

## 🎯 First Time Setup (30 seconds)

### Step 1: Login
1. Scroll to **"1. Authentication"** section
2. Click **POST /api/auth/login** to expand
3. Click **"Try it out"** button (top right)
4. You'll see 3 example logins - click one:
   - **Admin Login** - Full system access
   - **Doctor Login** - Clinical operations
   - **Receptionist Login** - Patient management
5. Click **"Execute"** button
6. Scroll down to see the response
7. **Copy the token** from the response (the long string after `"token":`)

### Step 2: Authorize
1. Scroll to the top of the page
2. Click the **"Authorize"** button (🔒 icon)
3. A dialog will open
4. Paste your token in the **"Value"** field
   - ⚠️ **Don't add "Bearer"** - just paste the token
5. Click **"Authorize"** button
6. Click **"Close"**
7. ✅ You're authenticated! The 🔒 icon should now be locked

### Step 3: Test APIs
- All endpoints are now accessible
- Navigate through sections 2-8
- Try different endpoints based on your role

---

## 📚 API Sections Overview

### 1. Authentication (Public Access)
**Purpose:** Login and get JWT token

**Endpoints:**
- `POST /api/auth/login` - Login with email/password

**Who can use:** Everyone (no authentication required)

**Example:**
```json
{
  "email": "doctor@curamatrix.com",
  "password": "doctor123"
}
```

---

### 2. Admin - User Management (ADMIN only)
**Purpose:** Create and manage system users

**Endpoints:**
- `POST /api/admin/users` - Create new user (Doctor/Receptionist/Admin)
- `GET /api/admin/users` - List all users
- `GET /api/admin/users/{id}` - Get user details
- `PUT /api/admin/users/{id}/deactivate` - Deactivate user
- `PUT /api/admin/users/{id}/activate` - Activate user

**Who can use:** Admin only

**Use case:** Add new doctors, receptionists, or admins to the system

---

### 3. Patients (RECEPTIONIST, DOCTOR)
**Purpose:** Patient registration and management

**Endpoints:**
- `POST /api/patients` - Register new patient (Receptionist)
- `GET /api/patients` - Search patients with filters
- `GET /api/patients/{id}` - Get patient details
- `PUT /api/patients/{id}` - Update patient info (Receptionist)

**Who can use:** 
- Receptionist: Full access
- Doctor: Read-only

**Use case:** Register walk-in patients, search patient records

---

### 4. Appointments (RECEPTIONIST, DOCTOR)
**Purpose:** Appointment booking and queue management

**Endpoints:**
- `POST /api/appointments` - Book scheduled appointment (Receptionist)
- `POST /api/appointments/walk-in` - Create walk-in with token (Receptionist)
- `GET /api/appointments` - List with filters (Receptionist)
- `GET /api/appointments/doctor/{doctorId}/slots` - Check available slots (Receptionist)
- `GET /api/appointments/doctor/{doctorId}/today` - Today's queue (Doctor)
- `PUT /api/appointments/{id}/status` - Update status (Both)

**Who can use:**
- Receptionist: Booking and management
- Doctor: View queue and update status

**Use case:** Book appointments, manage walk-in queue, track patient flow

---

### 5. Diagnosis (DOCTOR only)
**Purpose:** Create and manage patient diagnoses

**Endpoints:**
- `POST /api/diagnoses` - Create diagnosis
- `GET /api/diagnoses/{id}` - Get diagnosis details
- `GET /api/diagnoses/appointment/{appointmentId}` - Get by appointment
- `PUT /api/diagnoses/{id}` - Update diagnosis

**Who can use:** Doctor only

**Use case:** Document patient symptoms, diagnosis, and clinical notes

---

### 6. Prescriptions (DOCTOR only)
**Purpose:** Prescribe medicines to patients

**Endpoints:**
- `POST /api/prescriptions` - Add prescriptions (batch)
- `GET /api/prescriptions/diagnosis/{diagnosisId}` - Get prescriptions

**Who can use:** Doctor only

**Use case:** Prescribe multiple medicines with dosage and instructions

---

### 7. Medicine Search (DOCTOR, ADMIN)
**Purpose:** Fast autocomplete search for medicines

**Endpoints:**
- `GET /api/medicines/search` - Search by name/generic/brand

**Who can use:** Doctor, Admin

**Query parameters:**
- `query` (required, min 2 chars) - Search term

**Use case:** Find medicines while writing prescriptions

**Examples:**
- `?query=parac` → Paracetamol variants
- `?query=metf` → Metformin
- `?query=crocin` → Crocin brand

---

### 8. Departments (ADMIN, RECEPTIONIST)
**Purpose:** View hospital departments

**Endpoints:**
- `GET /api/departments` - List all active departments
- `GET /api/departments/{id}` - Get department details

**Who can use:** Admin, Receptionist

**Use case:** View departments when creating doctors or booking appointments

---

## 🎬 Complete Workflows in Swagger

### Workflow 1: Register Patient & Book Appointment

#### Step 1: Login as Receptionist
```
1. Authentication → POST /api/auth/login
2. Use "Receptionist Login" example
3. Execute → Copy token
4. Click Authorize → Paste token → Authorize
```

#### Step 2: Register Patient
```
1. Patients → POST /api/patients
2. Try it out
3. Modify the example JSON (change name, phone, etc.)
4. Execute
5. Note the "id" in response (e.g., 101)
```

#### Step 3: Check Available Slots
```
1. Appointments → GET /api/appointments/doctor/{doctorId}/slots
2. Try it out
3. doctorId: 1
4. date: 2026-03-15 (future date)
5. Execute
6. See available time slots
```

#### Step 4: Book Appointment
```
1. Appointments → POST /api/appointments
2. Try it out
3. Update JSON:
   {
     "patientId": 101,  // Use ID from Step 2
     "doctorId": 1,
     "appointmentDate": "2026-03-15",
     "appointmentTime": "10:30",  // Choose from available slots
     "notes": "Follow-up consultation"
   }
4. Execute
5. Note the appointment "id" (e.g., 501)
```

---

### Workflow 2: Doctor Consultation

#### Step 1: Login as Doctor
```
1. Authentication → POST /api/auth/login
2. Use "Doctor Login" example
3. Execute → Copy token
4. Authorize with new token
```

#### Step 2: View Today's Queue
```
1. Appointments → GET /api/appointments/doctor/{doctorId}/today
2. Try it out
3. doctorId: 1
4. Execute
5. See list of today's patients
```

#### Step 3: Start Consultation
```
1. Appointments → PUT /api/appointments/{id}/status
2. Try it out
3. id: 501 (from booking)
4. status: IN_PROGRESS
5. Execute
```

#### Step 4: Create Diagnosis
```
1. Diagnosis → POST /api/diagnoses
2. Try it out
3. Update JSON:
   {
     "appointmentId": 501,
     "symptoms": "High blood sugar, fatigue",
     "diagnosis": "Type 2 Diabetes",
     "clinicalNotes": "HbA1c: 8.5%",
     "severity": "MODERATE",
     "followUpDate": "2026-04-15"
   }
4. Execute
5. Note the diagnosis "id" (e.g., 301)
```

#### Step 5: Search Medicine
```
1. Medicine Search → GET /api/medicines/search
2. Try it out
3. query: metf
4. Execute
5. See Metformin options, note medicine IDs
```

#### Step 6: Add Prescriptions
```
1. Prescriptions → POST /api/prescriptions
2. Try it out
3. Update JSON:
   {
     "diagnosisId": 301,
     "prescriptions": [
       {
         "medicineId": 10,  // From search results
         "dosage": "500mg",
         "frequency": "Twice daily",
         "durationDays": 30,
         "instructions": "Take after meals"
       }
     ]
   }
4. Execute
```

#### Step 7: Complete Consultation
```
1. Appointments → PUT /api/appointments/{id}/status
2. Try it out
3. id: 501
4. status: COMPLETED
5. Execute
```

---

### Workflow 3: Create New Doctor (Admin)

#### Step 1: Login as Admin
```
1. Authentication → POST /api/auth/login
2. Use "Admin Login" example
3. Execute → Copy token → Authorize
```

#### Step 2: View Departments
```
1. Departments → GET /api/departments
2. Try it out
3. Execute
4. Note department IDs (e.g., 1 = General Medicine)
```

#### Step 3: Create Doctor User
```
1. Admin - User Management → POST /api/admin/users
2. Try it out
3. Update JSON:
   {
     "email": "dr-new@curamatrix.com",
     "password": "doctor123",
     "fullName": "Dr. New Doctor",
     "phone": "9876543299",
     "role": "ROLE_DOCTOR",
     "departmentId": 1,
     "specialization": "General Medicine",
     "licenseNumber": "MCI-GM-099",
     "qualification": "MBBS, MD",
     "experienceYears": 5,
     "consultationFee": 500.00
   }
4. Execute
5. New doctor created!
```

---

## 💡 Swagger UI Tips & Tricks

### 1. Example Values
- Click **"Example Value"** to auto-fill request body
- Modify only the fields you need
- Keep the structure intact

### 2. Schema View
- Click **"Schema"** tab to see field descriptions
- Shows required fields (marked with *)
- Shows data types and constraints

### 3. Response Codes
- **200** - Success (GET, PUT)
- **201** - Created (POST)
- **204** - No Content (DELETE)
- **400** - Bad Request (validation error)
- **401** - Unauthorized (no/invalid token)
- **403** - Forbidden (wrong role)
- **404** - Not Found
- **409** - Conflict (e.g., slot already booked)

### 4. Copy Response
- Click **"Copy"** icon next to response
- Paste into text editor
- Extract IDs for next requests

### 5. Clear Responses
- Click **"Clear"** to remove old responses
- Keeps the page clean
- Useful when testing multiple times

### 6. Download OpenAPI Spec
- Top of page: `/v3/api-docs`
- Download JSON specification
- Import into other tools

### 7. Expand/Collapse All
- Click section headers to expand/collapse
- Focus on one section at a time
- Easier navigation

### 8. Search Endpoints
- Use browser search (Ctrl+F / Cmd+F)
- Search for endpoint paths or descriptions
- Quick navigation to specific APIs

---

## 🔍 Testing Scenarios

### Scenario 1: Search Patients
```
1. Patients → GET /api/patients
2. Try it out
3. Test different searches:
   - search: amit (by name)
   - search: 9876543210 (by phone)
   - search: (empty = all patients)
4. Try pagination:
   - page: 0, size: 10
   - page: 1, size: 10
```

### Scenario 2: Filter Appointments
```
1. Appointments → GET /api/appointments
2. Try combinations:
   - date: 2026-03-15
   - doctorId: 1
   - status: BOOKED
   - type: WALK_IN
   - All filters together
```

### Scenario 3: Update Patient
```
1. Patients → GET /api/patients/{id}
2. Copy the response
3. Patients → PUT /api/patients/{id}
4. Paste and modify fields
5. Execute to update
```

---

## 🐛 Common Issues

### Issue: "Failed to fetch"
**Cause:** Application not running
**Fix:** 
```bash
./gradlew bootRun
```

### Issue: "401 Unauthorized"
**Cause:** Not logged in or token expired
**Fix:** 
1. Login again
2. Copy new token
3. Click Authorize
4. Paste token
5. Authorize

### Issue: "403 Forbidden"
**Cause:** Wrong role for endpoint
**Fix:**
- Check endpoint description for required role
- Login with correct user
- Admin endpoints need admin login
- Doctor endpoints need doctor login

### Issue: "400 Bad Request"
**Cause:** Validation error
**Fix:**
- Read error message in response
- Check required fields
- Verify data types (dates, enums)
- Use correct format (YYYY-MM-DD for dates)

### Issue: "404 Not Found"
**Cause:** Resource doesn't exist
**Fix:**
- Verify ID exists
- Use GET endpoints to find valid IDs
- Check if resource was deleted

### Issue: Can't see response
**Cause:** Response too large or browser issue
**Fix:**
- Scroll down in response section
- Click "Download" to save response
- Check browser console for errors

---

## 📊 Quick Reference

### Default Credentials
```
Admin:        admin@curamatrix.com / admin123
Doctor:       doctor@curamatrix.com / doctor123
Receptionist: reception@curamatrix.com / reception123
```

### Common IDs
```
Doctor ID:     1
Department ID: 1 (General Medicine)
Patient ID:    Create via POST /api/patients
```

### Date Format
```
Date:     YYYY-MM-DD (e.g., 2026-03-15)
Time:     HH:mm (e.g., 10:30)
DateTime: YYYY-MM-DDTHH:mm:ss (e.g., 2026-03-15T10:30:00)
```

### Enum Values
```
Gender:            MALE, FEMALE, OTHER
Blood Group:       A_POSITIVE, B_POSITIVE, O_POSITIVE, AB_POSITIVE, etc.
Appointment Type:  SCHEDULED, WALK_IN
Appointment Status: BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED
Severity:          MILD, MODERATE, SEVERE, CRITICAL
Role:              ROLE_ADMIN, ROLE_DOCTOR, ROLE_RECEPTIONIST
```

---

## 🎓 Learning Path

### Beginner (15 minutes)
1. Login as any user
2. Authorize with token
3. Try GET endpoints (read-only)
4. View departments, search patients

### Intermediate (30 minutes)
1. Register a patient
2. Book an appointment
3. Search medicines
4. Update appointment status

### Advanced (1 hour)
1. Complete full patient journey
2. Create diagnosis with prescriptions
3. Create new users (as admin)
4. Test all workflows

---

## 📞 Need More Help?

- **Detailed Testing Guide**: See `API_TESTING_GUIDE.md`
- **Postman Collection**: See `postman/README.md`
- **User Setup**: See `SETUP_USERS.md`
- **Support**: support@curamatrix.com

---

**Happy Testing with Swagger! 🎉**
