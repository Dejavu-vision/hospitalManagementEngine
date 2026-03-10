# Postman Collection Setup Guide

## Quick Start

### 1. Import Collection and Environment

1. **Open Postman**
2. **Import Collection:**
   - Click `Import` button (top left)
   - Select `CuraMatrix_HSM.postman_collection.json`
   - Click `Import`

3. **Import Environment:**
   - Click `Import` button again
   - Select `CuraMatrix_HSM.postman_environment.json`
   - Click `Import`

4. **Select Environment:**
   - Click the environment dropdown (top right)
   - Select `CuraMatrix HSM - Local`

### 2. Configure Base URL (if needed)

If your backend is running on a different port or host:

1. Click on the environment name (top right)
2. Click `Edit` (or the eye icon)
3. Update `baseUrl` variable:
   - Default: `http://localhost:8080`
   - Change if your server runs on different port/host

### 3. Login First

**Important:** You must login before using other endpoints.

1. Go to **Authentication** folder
2. Run one of the login requests:
   - **Login - Admin** (for admin endpoints)
   - **Login - Doctor** (for doctor endpoints)
   - **Login - Receptionist** (for receptionist endpoints)

3. The JWT token will be **automatically saved** to the `token` environment variable
4. All other requests will use this token automatically

### 4. Test Medicine Search

1. Make sure you're logged in as **Doctor** or **Admin**
2. Go to **Medicine Search** folder
3. Run **Search Medicines - Paracetamol**
4. You should see results like:
   ```json
   [
     {
       "id": 1,
       "name": "Paracetamol",
       "strength": "500mg",
       "form": "TABLET"
     }
   ]
   ```

---

## Collection Structure

```
CuraMatrix HSM API
├── Authentication
│   ├── Login - Admin
│   ├── Login - Doctor
│   └── Login - Receptionist
├── Medicine Search ⭐ (Just Implemented)
│   ├── Search Medicines - Paracetamol
│   ├── Search Medicines - Amoxicillin
│   ├── Search Medicines - Generic Name
│   ├── Search Medicines - Brand Name
│   └── Search Medicines - Invalid Query (Too Short)
├── Patients
│   ├── Register Patient
│   ├── Search Patients
│   └── Get Patient by ID
├── Appointments
│   ├── Book Scheduled Appointment
│   ├── Create Walk-in Appointment
│   ├── Get Doctor's Today Queue
│   └── Get Available Slots
├── Diagnosis & Prescriptions
│   ├── Create Diagnosis
│   ├── Add Prescriptions
│   └── Get Prescriptions by Diagnosis
├── Billing
│   ├── Create Billing
│   └── Update Payment Status
└── Admin
    ├── Get All Departments
    └── Create Department
```

---

## Environment Variables

| Variable | Description | Auto-Updated |
|----------|-------------|--------------|
| `baseUrl` | Backend base URL | No |
| `token` | JWT authentication token | ✅ Yes (after login) |
| `userId` | Current user ID | ✅ Yes (after login) |
| `userRole` | Current user role | ✅ Yes (after login) |
| `patientId` | Last created patient ID | No (manual) |
| `doctorId` | Doctor ID (default: 1) | No |
| `appointmentId` | Last created appointment ID | No (manual) |
| `diagnosisId` | Last created diagnosis ID | No (manual) |
| `billingId` | Last created billing ID | No (manual) |

---

## Default Login Credentials

| Role | Email | Password |
|------|-------|----------|
| **Admin** | `admin@curamatrix.com` | `admin123` |
| **Doctor** | `doctor@curamatrix.com` | `doctor123` |
| **Receptionist** | `reception@curamatrix.com` | `reception123` |

> **Note:** These are seed data credentials from the database DDL script. Make sure you've run `schema.sql` to create these users.

---

## Testing Medicine Search API

### Valid Searches

1. **By Medicine Name:**
   ```
   GET /api/medicines/search?query=parac
   ```
   Returns: Paracetamol variants

2. **By Generic Name:**
   ```
   GET /api/medicines/search?query=acetamin
   ```
   Returns: Medicines with Acetaminophen generic name

3. **By Brand Name:**
   ```
   GET /api/medicines/search?query=crocin
   ```
   Returns: Crocin brand medicines

### Invalid Search (Validation Test)

```
GET /api/medicines/search?query=p
```
Returns: `400 Bad Request` (query too short, minimum 2 characters)

---

## Tips

### 1. Auto-Save Token
The login requests automatically save the JWT token to the environment. You don't need to manually copy-paste tokens.

### 2. Use Variables
Instead of hardcoding IDs, use environment variables:
- `{{patientId}}` instead of `1`
- `{{doctorId}}` instead of `1`
- `{{token}}` is automatically used in Authorization header

### 3. Test Different Roles
- Switch between Admin, Doctor, and Receptionist logins to test role-based access
- Some endpoints require specific roles

### 4. Response Time Test
All requests include an automatic test that checks response time < 5000ms

### 5. Update IDs Manually
After creating resources (patient, appointment, etc.), update the corresponding environment variable for easier testing of related endpoints.

---

## Troubleshooting

### Issue: "401 Unauthorized"
**Solution:** 
- Make sure you've logged in first
- Check that `token` variable is set in environment
- Token might have expired (login again)

### Issue: "403 Forbidden"
**Solution:**
- You're logged in with the wrong role
- Use the correct login request for the endpoint you're testing
- Example: Medicine Search requires `ROLE_DOCTOR` or `ROLE_ADMIN`

### Issue: "Connection Refused"
**Solution:**
- Make sure Spring Boot application is running
- Check `baseUrl` is correct (default: `http://localhost:8080`)
- Verify port 8080 is not blocked

### Issue: "404 Not Found"
**Solution:**
- Check the endpoint URL is correct
- Make sure the backend endpoint is implemented
- Verify `baseUrl` variable is set correctly

---

## Next Steps

1. ✅ Import collection and environment
2. ✅ Login as Doctor or Admin
3. ✅ Test Medicine Search endpoint
4. ✅ Explore other endpoints as you implement them
5. ✅ Update collection as you add new endpoints

---

**Happy Testing! 🚀**
