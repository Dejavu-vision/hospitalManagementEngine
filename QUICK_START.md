# Quick Start Guide - CuraMatrix HSM

Get up and running in 5 minutes!

---

## ⚡ 5-Minute Setup

### 1. Database Setup (2 minutes)
```bash
# Create database and load seed data
mysql -u root -p < docs/schema.sql
```

This creates:
- ✅ Database: `hospitalsystems`
- ✅ 14 tables
- ✅ 3 users (admin, doctor, receptionist)
- ✅ 10 departments
- ✅ 25 medicines with inventory

### 2. Configure Database (30 seconds)
Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    username: root
    password: your_mysql_password  # Change this
```

### 3. Start Application (1 minute)
```bash
./gradlew bootRun
```

Wait for: `Started HsmApplication in X seconds`

### 4. Test API (1 minute)
Open browser: `http://localhost:8080/swagger-ui.html`

1. Click **"1. Authentication"** → **POST /api/auth/login**
2. Click **"Try it out"**
3. Select **"Doctor Login"** example
4. Click **"Execute"**
5. Copy the `token` from response
6. Click **"Authorize"** 🔒 at top
7. Paste token → **"Authorize"** → **"Close"**
8. ✅ You're in! Try any endpoint

---

## 🎯 What Can You Do Now?

### As Doctor (doctor@curamatrix.com / doctor123)
- ✅ View today's patient queue
- ✅ Create diagnosis
- ✅ Search medicines
- ✅ Write prescriptions
- ✅ Update appointment status

### As Receptionist (reception@curamatrix.com / reception123)
- ✅ Register new patients
- ✅ Book appointments
- ✅ Create walk-in queue
- ✅ Check available slots
- ✅ Search patient records

### As Admin (admin@curamatrix.com / admin123)
- ✅ Create new users (doctors, receptionists)
- ✅ View all users
- ✅ Deactivate/activate users
- ✅ View departments
- ✅ Access all read operations

---

## 🚀 Choose Your Testing Tool

### Option 1: Swagger UI (In Browser)
**Best for:** Quick testing, API exploration

**Access:** `http://localhost:8080/swagger-ui.html`

**Pros:**
- No installation needed
- Interactive documentation
- Try APIs instantly
- Built-in authentication

**Guide:** See `SWAGGER_GUIDE.md`

---

### Option 2: Postman (Desktop App)
**Best for:** Comprehensive testing, workflows

**Setup:**
1. Import: `postman/CuraMatrix_HSM.postman_collection.json`
2. Import: `postman/CuraMatrix_HSM.postman_environment.json`
3. Select environment: "CuraMatrix HSM - Local"
4. Login first (token auto-saves)

**Pros:**
- 50+ pre-configured requests
- Auto-save tokens and IDs
- Organized workflows
- Request history

**Guide:** See `postman/README.md`

---

## 📚 Documentation Quick Links

### For Testing
- **Swagger Guide**: `SWAGGER_GUIDE.md` - Interactive API testing
- **API Testing Guide**: `API_TESTING_GUIDE.md` - Complete workflows
- **Postman Guide**: `postman/README.md` - Collection setup

### For Development
- **Backend Guide**: `docs/BACKEND_ENGINEER_GUIDE.md` - Technical docs
- **Frontend Guide**: `docs/FRONTEND_INTEGRATION_GUIDE.md` - API integration
- **Database Guide**: `docs/DATABASE_DDL.md` - Schema details

### For Setup
- **User Setup**: `SETUP_USERS.md` - Add users to system
- **README**: `README.md` - Project overview

---

## 🎬 Try These Workflows

### Workflow 1: Register Patient (2 minutes)
```
1. Login as Receptionist
2. POST /api/patients - Register new patient
3. GET /api/patients?search=name - Search for patient
4. ✅ Patient in system!
```

### Workflow 2: Book Appointment (3 minutes)
```
1. Login as Receptionist
2. GET /api/appointments/doctor/1/slots?date=2026-03-15
3. POST /api/appointments - Book with available slot
4. ✅ Appointment booked!
```

### Workflow 3: Doctor Consultation (5 minutes)
```
1. Login as Doctor
2. GET /api/appointments/doctor/1/today - View queue
3. PUT /api/appointments/{id}/status?status=IN_PROGRESS
4. POST /api/diagnoses - Create diagnosis
5. GET /api/medicines/search?query=metf - Search medicine
6. POST /api/prescriptions - Add prescriptions
7. PUT /api/appointments/{id}/status?status=COMPLETED
8. ✅ Patient treated!
```

### Workflow 4: Create New Doctor (2 minutes)
```
1. Login as Admin
2. GET /api/departments - View departments
3. POST /api/admin/users - Create doctor with department
4. ✅ New doctor added!
```

---

## 🔑 Default Login Credentials

| Role | Email | Password |
|------|-------|----------|
| **Admin** | admin@curamatrix.com | admin123 |
| **Doctor** | doctor@curamatrix.com | doctor123 |
| **Receptionist** | reception@curamatrix.com | reception123 |

---

## 📊 API Endpoints Summary

### Public
- `POST /api/auth/login` - Login

### Admin Only
- `POST /api/admin/users` - Create user
- `GET /api/admin/users` - List users
- `PUT /api/admin/users/{id}/deactivate` - Deactivate

### Receptionist
- `POST /api/patients` - Register patient
- `GET /api/patients` - Search patients
- `POST /api/appointments` - Book appointment
- `POST /api/appointments/walk-in` - Create walk-in
- `GET /api/appointments/doctor/{id}/slots` - Check slots

### Doctor
- `GET /api/appointments/doctor/{id}/today` - Today's queue
- `POST /api/diagnoses` - Create diagnosis
- `GET /api/medicines/search` - Search medicines
- `POST /api/prescriptions` - Add prescriptions
- `PUT /api/appointments/{id}/status` - Update status

### Both (Doctor & Receptionist)
- `GET /api/patients/{id}` - Get patient details
- `GET /api/departments` - List departments

---

## 🐛 Common Issues

### "Connection refused"
**Fix:** Start the application
```bash
./gradlew bootRun
```

### "Access denied for user 'root'"
**Fix:** Update password in `application.yml`
```yaml
spring:
  datasource:
    password: your_actual_password
```

### "Unknown database 'hospitalsystems'"
**Fix:** Run the schema script
```bash
mysql -u root -p < docs/schema.sql
```

### "401 Unauthorized"
**Fix:** Login first and authorize with token
1. POST /api/auth/login
2. Copy token
3. Click Authorize 🔒
4. Paste token

### "403 Forbidden"
**Fix:** Login with correct role
- Admin endpoints need admin login
- Doctor endpoints need doctor login
- Receptionist endpoints need receptionist login

---

## 🎓 Next Steps

### Learn More
1. **Explore Swagger UI** - Try all endpoints
2. **Read API Testing Guide** - Learn workflows
3. **Import Postman Collection** - Test comprehensively
4. **Read Backend Guide** - Understand architecture

### Customize
1. **Add More Users** - See `SETUP_USERS.md`
2. **Modify Database** - See `docs/DATABASE_DDL.md`
3. **Integrate Frontend** - See `docs/FRONTEND_INTEGRATION_GUIDE.md`

### Deploy
1. **Configure Production DB** - Update `application.yml`
2. **Build JAR** - `./gradlew build`
3. **Run** - `java -jar build/libs/hsm-0.0.1-SNAPSHOT.jar`

---

## 📞 Support

- **Email**: support@curamatrix.com
- **Documentation**: Check `docs/` folder
- **Issues**: Review error messages in console

---

## ✅ Checklist

- [ ] Database created and seeded
- [ ] Application running on port 8080
- [ ] Swagger UI accessible
- [ ] Successfully logged in
- [ ] Token authorized
- [ ] Tested at least one endpoint
- [ ] Ready to explore!

---

**You're all set! Start testing the APIs! 🎉**
