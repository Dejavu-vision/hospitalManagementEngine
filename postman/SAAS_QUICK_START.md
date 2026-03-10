# Multi-Tenant SaaS - Postman Quick Start

## 🚀 Setup (2 minutes)

### Step 1: Import Environment
1. Open Postman
2. Click **Import**
3. Select `CuraMatrix_HSM.postman_environment.json`
4. Click **Import**

### Step 2: Select Environment
1. Click environment dropdown (top right)
2. Select **"CuraMatrix HSM - Multi-Tenant SaaS"**

### Step 3: Run Migration
```bash
mysql -u root -p < docs/MULTI_TENANT_MIGRATION.sql
```

---

## 🎯 Quick Test Flow

### Test 1: Super Admin Operations

**1. Login as Super Admin**
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "superadmin@curamatrix.com",
  "password": "admin123",
  "tenantKey": "default-hospital"
}
```
✅ Token auto-saved to environment

**2. Register New Hospital**
```http
POST http://localhost:8080/api/super-admin/tenants
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PREMIUM",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2027-03-11",
  "contactEmail": "contact@apollo-mumbai.com",
  "adminFullName": "Apollo Admin",
  "adminEmail": "admin@apollo-mumbai.com",
  "adminPassword": "admin123"
}
```
✅ Hospital created with admin user

**3. View All Hospitals**
```http
GET http://localhost:8080/api/super-admin/tenants
Authorization: Bearer {{token}}
```

---

### Test 2: Hospital Operations

**1. Login to Apollo Mumbai**
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "admin@apollo-mumbai.com",
  "password": "admin123",
  "tenantKey": "apollo-mumbai"
}
```
✅ Now operating in Apollo Mumbai context

**2. Create Doctor**
```http
POST http://localhost:8080/api/admin/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "email": "doctor@apollo-mumbai.com",
  "password": "doctor123",
  "fullName": "Dr. Rajesh Sharma",
  "phone": "9876543212",
  "role": "ROLE_DOCTOR",
  "departmentId": 1,
  "specialization": "Cardiology",
  "licenseNumber": "MCI-CARD-001",
  "consultationFee": 1500.00
}
```

**3. Create Receptionist**
```http
POST http://localhost:8080/api/admin/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "email": "reception@apollo-mumbai.com",
  "password": "reception123",
  "fullName": "Priya Patel",
  "phone": "9876543213",
  "role": "ROLE_RECEPTIONIST",
  "employeeId": "REC-APO-001",
  "shift": "MORNING"
}
```

---

### Test 3: Patient Journey

**1. Login as Receptionist**
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "reception@apollo-mumbai.com",
  "password": "reception123",
  "tenantKey": "apollo-mumbai"
}
```

**2. Register Patient**
```http
POST http://localhost:8080/api/patients
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit@email.com",
  "bloodGroup": "B_POSITIVE"
}
```
✅ Patient ID auto-saved

**3. Book Appointment**
```http
POST http://localhost:8080/api/appointments
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "patientId": {{patientId}},
  "doctorId": 1,
  "appointmentDate": "2026-03-20",
  "appointmentTime": "10:30",
  "notes": "Regular checkup"
}
```

---

### Test 4: Multi-Tenant Isolation

**1. Create Another Hospital**
```http
POST http://localhost:8080/api/super-admin/tenants
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "tenantKey": "fortis-delhi",
  "hospitalName": "Fortis Hospital Delhi",
  "subscriptionPlan": "STANDARD",
  "subscriptionStart": "2026-03-11",
  "subscriptionEnd": "2027-03-11",
  "contactEmail": "contact@fortis-delhi.com",
  "adminFullName": "Fortis Admin",
  "adminEmail": "admin@fortis-delhi.com",
  "adminPassword": "admin123"
}
```

**2. Login to Fortis Delhi**
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "admin@fortis-delhi.com",
  "password": "admin123",
  "tenantKey": "fortis-delhi"
}
```

**3. Try to Access Apollo's Patient**
```http
GET http://localhost:8080/api/patients/{{patientId}}
Authorization: Bearer {{token}}
```
❌ Should return 404 - Data isolation working!

---

## 📋 All Available Requests

See `SAAS_POSTMAN_REQUESTS.md` for complete list of 70+ requests including:

### 0. Super Admin (10 requests)
- Login as Super Admin
- Register/Update/Suspend/Activate Tenants
- View tenant statistics

### 1. Authentication (5 requests)
- Login to different hospitals
- Multi-tenant login with tenantKey

### 2. Admin - User Management (7 requests)
- Create doctors, receptionists, admins
- Manage users within tenant

### 3. Patients (5 requests)
- Register, search, update patients
- Tenant-isolated data

### 4. Appointments (8 requests)
- Book scheduled/walk-in appointments
- View queue, check slots

### 5. Diagnosis & Prescriptions (6 requests)
- Create diagnosis
- Add prescriptions

### 6. Medicine Search (5 requests)
- Search by name, generic, brand

### 7. Departments (2 requests)
- View departments

---

## 🔑 Default Credentials

### Super Admin (Platform Level)
```
Email: superadmin@curamatrix.com
Password: admin123
Tenant: default-hospital
```

### Default Hospital
```
Admin: admin@curamatrix.com / admin123
Doctor: doctor@curamatrix.com / doctor123
Receptionist: reception@curamatrix.com / reception123
Tenant: default-hospital
```

### Apollo Mumbai (After Registration)
```
Admin: admin@apollo-mumbai.com / admin123
Tenant: apollo-mumbai
```

### Fortis Delhi (After Registration)
```
Admin: admin@fortis-delhi.com / admin123
Tenant: fortis-delhi
```

---

## 🎓 Key Concepts

### 1. Tenant Key
- Unique identifier for each hospital
- Required in every login request
- Format: lowercase-with-hyphens
- Examples: `apollo-mumbai`, `fortis-delhi`

### 2. JWT Token
- Contains tenant context
- Automatically filters all queries
- Includes: tenantId, tenantKey, hospitalName
- Expires in 24 hours

### 3. Data Isolation
- All data filtered by tenant_id
- Cross-tenant access returns 404
- Automatic tenant assignment on create

### 4. Subscription Plans
- **BASIC**: 10 users, 1000 patients, $99/month
- **STANDARD**: 50 users, 10000 patients, $499/month
- **PREMIUM**: Unlimited, $1999/month

---

## 🐛 Troubleshooting

### "Tenant not found"
- Check tenantKey spelling
- Verify tenant exists: `GET /api/super-admin/tenants`

### "Subscription expired"
- Check subscription end date
- Extend via: `PUT /api/super-admin/tenants/{id}`

### "Quota exceeded"
- Check current usage: `GET /api/super-admin/tenants/{id}/stats`
- Upgrade plan if needed

### "User does not belong to this hospital"
- Verify tenantKey matches user's hospital
- Check user was created in correct tenant

---

## 📊 Testing Checklist

- [ ] Super admin can create tenants
- [ ] Hospital admin can create users
- [ ] Users can only see their tenant's data
- [ ] Cross-tenant access blocked
- [ ] Subscription expiry enforced
- [ ] Quota limits enforced
- [ ] Token includes tenant info
- [ ] All CRUD operations work per tenant

---

## 🎉 You're Ready!

1. ✅ Environment configured
2. ✅ Migration run
3. ✅ Super admin working
4. ✅ Multi-tenant isolation tested
5. ✅ Ready for production!

For complete API documentation, see:
- `SAAS_POSTMAN_REQUESTS.md` - All 70+ requests
- `SAAS_ARCHITECTURE.md` - Technical architecture
- `API_TESTING_GUIDE.md` - Testing workflows
