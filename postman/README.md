# Postman Collection - Quick Start

## 📦 What's Included

- **Collection**: `CuraMatrix_HSM.postman_collection.json` - 50+ API requests
- **Environment**: `CuraMatrix_HSM.postman_environment.json` - Variables and configuration

## 🚀 Setup (2 minutes)

### Step 1: Import Files
1. Open Postman
2. Click **Import** button (top left)
3. Drag both JSON files or click **"Choose Files"**
4. Click **Import**

### Step 2: Select Environment
1. Click environment dropdown (top right corner)
2. Select **"CuraMatrix HSM - Local"**
3. ✅ You're ready!

## 🔐 Authentication Flow

### First Time Setup
1. Open **"1. Authentication"** folder
2. Run **"Login - Admin"** (or Doctor/Receptionist)
3. ✅ Token automatically saved to environment
4. All other requests now work!

### Switch Roles
Just run a different login request:
- **Login - Admin** → Full access
- **Login - Doctor** → Diagnosis & prescriptions
- **Login - Receptionist** → Patients & appointments

## 📋 Collection Structure

```
CuraMatrix HSM API
├── 1. Authentication (3 requests)
│   ├── Login - Admin
│   ├── Login - Doctor
│   └── Login - Receptionist
│
├── 2. Admin - User Management (7 requests)
│   ├── Create Doctor User
│   ├── Create Receptionist User
│   ├── Create Admin User
│   ├── Get All Users
│   ├── Get User by ID
│   ├── Deactivate User
│   └── Activate User
│
├── 3. Patients (4 requests)
│   ├── Register Patient
│   ├── Search Patients
│   ├── Get Patient by ID
│   └── Update Patient
│
├── 4. Appointments (8 requests)
│   ├── Book Scheduled Appointment
│   ├── Create Walk-in Appointment
│   ├── Get Appointments (All Filters)
│   ├── Get Available Slots
│   ├── Get Doctor's Today Queue
│   ├── Update Status - CHECKED_IN
│   ├── Update Status - IN_PROGRESS
│   └── Update Status - COMPLETED
│
├── 5. Diagnosis & Prescriptions (6 requests)
│   ├── Create Diagnosis
│   ├── Get Diagnosis by ID
│   ├── Get Diagnosis by Appointment
│   ├── Update Diagnosis
│   ├── Add Prescriptions (Batch)
│   └── Get Prescriptions by Diagnosis
│
├── 6. Medicine Search (5 requests)
│   ├── Search - Paracetamol
│   ├── Search - Amoxicillin
│   ├── Search - Generic Name
│   ├── Search - Brand Name
│   └── Search - Metformin
│
└── 7. Departments (2 requests)
    ├── Get All Departments
    └── Get Department by ID
```

## 🎯 Quick Test Workflows

### Workflow 1: Register Patient & Book Appointment
1. **Login - Receptionist**
2. **Register Patient** → Save `patientId`
3. **Get Available Slots** → Choose a time
4. **Book Scheduled Appointment** → Use saved `patientId`
5. ✅ Appointment created!

### Workflow 2: Doctor Consultation
1. **Login - Doctor**
2. **Get Doctor's Today Queue** → See patients
3. **Update Status - IN_PROGRESS** → Start consultation
4. **Create Diagnosis** → Document findings
5. **Search - Metformin** → Find medicine
6. **Add Prescriptions** → Prescribe medicines
7. **Update Status - COMPLETED** → Finish consultation
8. ✅ Patient treated!

### Workflow 3: Create New Doctor
1. **Login - Admin**
2. **Get All Departments** → Choose department
3. **Create Doctor User** → Add new doctor
4. ✅ Doctor account created!

## 🔧 Environment Variables

These are automatically managed:

| Variable | Description | Auto-Updated |
|----------|-------------|--------------|
| `baseUrl` | API base URL | No (default: localhost:8080) |
| `token` | JWT token | ✅ Yes (after login) |
| `userId` | Current user ID | ✅ Yes (after login) |
| `userRole` | Current role | ✅ Yes (after login) |
| `doctorId` | Doctor ID | No (default: 1) |
| `patientId` | Last created patient | ✅ Yes (after patient creation) |
| `appointmentId` | Last created appointment | ✅ Yes (after appointment creation) |
| `diagnosisId` | Last created diagnosis | ✅ Yes (after diagnosis creation) |

## 💡 Pro Tips

### 1. Auto-Save IDs
After creating resources, IDs are automatically saved:
```javascript
// In "Tests" tab of requests
if (pm.response.code === 201) {
    var jsonData = pm.response.json();
    pm.environment.set("patientId", jsonData.id);
}
```

### 2. Use Variables in Requests
```json
{
  "patientId": {{patientId}},
  "doctorId": {{doctorId}}
}
```

### 3. Check Response Times
All requests include automatic tests:
```javascript
pm.test("Response time < 5000ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(5000);
});
```

### 4. View Environment Variables
- Click 👁️ icon next to environment dropdown
- See all current values
- Manually edit if needed

### 5. Duplicate Requests
- Right-click any request → **Duplicate**
- Modify for different test scenarios
- Keep original as reference

## 🐛 Troubleshooting

### "401 Unauthorized"
**Fix:** Run a login request first
- Token might be expired
- Token not set in environment

### "403 Forbidden"
**Fix:** Login with correct role
- Admin endpoints need admin login
- Doctor endpoints need doctor login
- Receptionist endpoints need receptionist login

### "404 Not Found"
**Fix:** Check the ID exists
- Run "Get All" request first
- Verify ID in environment variables

### "Connection Refused"
**Fix:** Start the application
```bash
./gradlew bootRun
```

### Variables Not Saving
**Fix:** Check environment is selected
- Look at top-right dropdown
- Should show "CuraMatrix HSM - Local"
- Not "No Environment"

## 📊 Default Test Data

### Login Credentials
```
Admin:        admin@curamatrix.com / admin123
Doctor:       doctor@curamatrix.com / doctor123
Receptionist: reception@curamatrix.com / reception123
```

### Department IDs
```
1 = General Medicine
2 = Cardiology
3 = Orthopedics
4 = Pediatrics
5 = Dermatology
```

### Medicine IDs (for prescriptions)
```
1-3   = Paracetamol variants
10-11 = Metformin (diabetes)
12-13 = Glimepiride (diabetes)
14    = Amlodipine (blood pressure)
```

## 🎓 Learning Path

### Beginner
1. Run all requests in **"1. Authentication"**
2. Try **"3. Patients"** → Register and search
3. Explore **"7. Departments"** → View departments

### Intermediate
1. Complete **Workflow 1** (Register & Book)
2. Test **"4. Appointments"** → All appointment types
3. Try **"6. Medicine Search"** → Different queries

### Advanced
1. Complete **Workflow 2** (Full consultation)
2. Test **"2. Admin"** → User management
3. Create custom workflows combining multiple requests

## 📞 Need Help?

- **Full Testing Guide**: See `../API_TESTING_GUIDE.md`
- **User Setup**: See `../SETUP_USERS.md`
- **API Documentation**: Open `http://localhost:8080/swagger-ui.html`
- **Support**: support@curamatrix.com

---

**Happy Testing! 🚀**
