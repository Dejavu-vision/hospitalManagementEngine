# CuraMatrix Hospital Management System (HSM) - Multi-Tenant SaaS

A comprehensive **Multi-Tenant SaaS** Hospital Management System built with Spring Boot 3.5.11, providing REST APIs for multiple hospitals with complete data isolation, subscription management, and scalable architecture.

## 🌟 SaaS Features

- ✅ **Multi-Tenant Architecture** - 100+ hospitals on single instance
- ✅ **Complete Data Isolation** - Tenant-based row-level security
- ✅ **Subscription Management** - 3 pricing tiers (Basic/Standard/Premium)
- ✅ **Quota Enforcement** - User, patient, and API limits
- ✅ **Super Admin Portal** - Platform-level hospital management
- ✅ **Horizontal Scalability** - Load balancer ready
- ✅ **Usage Analytics** - Per-tenant statistics and billing

## ⚡ Quick Start

**Get running in 5 minutes!** See **[QUICK_START.md](QUICK_START.md)**

```bash
# 1. Setup database with complete multi-tenant schema (ALL-IN-ONE)
mysql -u root -p < docs/COMPLETE_SAAS_SCHEMA.sql

# 2. Configure password in src/main/resources/application.yml

# 3. Start application
./gradlew bootRun

# 4. Login as Super Admin
# http://localhost:8080/swagger-ui.html
# Email: superadmin@curamatrix.com
# Password: admin123
# Tenant: default-hospital
```

**📄 One SQL File:** `docs/COMPLETE_SAAS_SCHEMA.sql` - Complete schema + seed data
**📖 SQL Reference:** `docs/SQL_QUICK_REFERENCE.md` - What's included

## 🏥 Multi-Tenant Login

All logins now require **tenantKey**:

```json
{
  "email": "admin@apollo-mumbai.com",
  "password": "admin123",
  "tenantKey": "apollo-mumbai"
}
```

## Features

### Multi-Tenant SaaS
- **Tenant Management** - Register and manage multiple hospitals
- **Data Isolation** - Complete separation between hospitals
- **Subscription Plans** - Basic ($99), Standard ($499), Premium ($1,999)
- **Quota Management** - Enforce user and patient limits
- **Usage Tracking** - Monitor API calls and storage per tenant

### Hospital Operations
- **JWT Authentication** - Secure role-based access control with tenant context
- **Patient Management** - Register and manage patient records (tenant-isolated)
- **Appointment System** - Scheduled appointments and walk-in queue management
- **Diagnosis & Prescriptions** - Doctor workflow for patient diagnosis and medicine prescriptions
- **Medicine Search** - Fast autocomplete search for medicines
- **Department Management** - Hospital department organization
- **Swagger Documentation** - Interactive API documentation

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.11**
- **Spring Security + JWT**
- **Spring Data JPA**
- **MySQL 8.x**
- **Lombok**
- **Springdoc OpenAPI (Swagger)**

## Quick Start

### Prerequisites

- Java 17 or higher
- MySQL 8.x
- Gradle 8.x

### 1. Database Setup

```bash
# Create database and run schema
mysql -u root -p < docs/schema.sql
```

This will create:
- Database: `hospitalsystems`
- 14 tables with seed data
- 3 default users (admin, doctor, receptionist)
- 10 departments
- 25 medicines with inventory

### 2. Configure Database

Update `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hospitalsystems
    username: root
    password: your_password
```

### 3. Build and Run

```bash
# Build
./gradlew build

# Run
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### 4. Access Swagger UI

Open: `http://localhost:8080/swagger-ui.html`

## Default Login Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@curamatrix.com | admin123 |
| Doctor | doctor@curamatrix.com | doctor123 |
| Receptionist | reception@curamatrix.com | reception123 |

## API Endpoints

### Super Admin (Platform Level)
- `POST /api/super-admin/tenants` - Register new hospital
- `GET /api/super-admin/tenants` - List all hospitals
- `GET /api/super-admin/tenants/{id}/stats` - Usage statistics
- `PUT /api/super-admin/tenants/{id}/suspend` - Suspend hospital
- `PUT /api/super-admin/tenants/{id}/activate` - Activate hospital

### Authentication (Multi-Tenant)
- `POST /api/auth/login` - Login with tenantKey (returns JWT with tenant context)

### Admin (Tenant Level)
- `POST /api/admin/users` - Create user (Doctor/Receptionist/Admin)
- `GET /api/admin/users` - List users (tenant-filtered)
- `PUT /api/admin/users/{id}/deactivate` - Deactivate user

### Patients (Tenant Isolated)
- `POST /api/patients` - Register new patient
- `GET /api/patients` - Search patients (tenant-filtered)
- `GET /api/patients/{id}` - Get patient details
- `PUT /api/patients/{id}` - Update patient

### Appointments (Tenant Isolated)
- `POST /api/appointments` - Book scheduled appointment
- `POST /api/appointments/walk-in` - Create walk-in appointment
- `GET /api/appointments` - List appointments (with filters)
- `GET /api/appointments/doctor/{id}/slots` - Get available time slots
- `PUT /api/appointments/{id}/status` - Update appointment status

### Doctor Operations (Tenant Isolated)
- `GET /api/appointments/doctor/{id}/today` - Get today's patient queue
- `POST /api/diagnoses` - Create diagnosis
- `POST /api/prescriptions` - Add prescriptions (batch)
- `GET /api/medicines/search` - Search medicines (autocomplete)

### Departments (Tenant Isolated)
- `GET /api/departments` - List all active departments
- `GET /api/departments/{id}` - Get department details

## API Testing

### Swagger UI (Interactive Documentation)

Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

**Features:**
- Interactive API documentation
- Try endpoints directly in browser
- Built-in authentication
- Request/response examples
- Organized by modules

**Quick Start:**
1. Click on **"1. Authentication"** → **POST /api/auth/login**
2. Click **"Try it out"** → Select example → **"Execute"**
3. Copy the `token` from response
4. Click **"Authorize"** 🔒 button at top → Paste token → **"Authorize"**
5. Now test any endpoint!

### Postman Collection

1. Import collection: `postman/CuraMatrix_HSM.postman_collection.json`
2. Import environment: `postman/CuraMatrix_HSM.postman_environment.json`
3. Select environment: **"CuraMatrix HSM - Local"**
4. Login first (token auto-saves to environment)
5. Test other endpoints

**Collection includes:**
- 50+ pre-configured requests
- Auto-save tokens and IDs
- Request examples for all endpoints
- Organized by workflow

### Complete Testing Guide

See **[API_TESTING_GUIDE.md](API_TESTING_GUIDE.md)** for:
- Step-by-step workflows
- Testing scenarios
- Common issues & solutions
- Best practices

## Project Structure

```
src/main/java/com/curamatrix/hsm/
├── config/              # Security, JWT configuration
├── controller/          # REST controllers
├── dto/                 # Request/Response DTOs
│   ├── request/
│   └── response/
├── entity/              # JPA entities
├── enums/               # Enum types
├── repository/          # Spring Data repositories
└── service/             # Business logic
```

## 📚 Complete Documentation

### 🚀 Getting Started
- **[Quick Start Guide](QUICK_START.md)** - Get running in 5 minutes
- **[SaaS Architecture](SAAS_ARCHITECTURE.md)** - Multi-tenant design and implementation
- **[Multi-Tenant Migration](docs/MULTI_TENANT_MIGRATION.sql)** - Database upgrade script
- **[User Setup Guide](SETUP_USERS.md)** - Add users to the system

### 🧪 API Testing
- **[Postman Quick Start](postman/SAAS_QUICK_START.md)** - 2-minute setup guide
- **[All SaaS Requests](postman/SAAS_POSTMAN_REQUESTS.md)** - 70+ API requests
- **[API Testing Guide](API_TESTING_GUIDE.md)** - Complete workflows and scenarios
- **[Swagger UI Guide](SWAGGER_GUIDE.md)** - Interactive API testing
- **[Swagger UI](http://localhost:8080/swagger-ui.html)** - Live documentation (after starting app)

### 👨‍💻 Development
- **[Backend Engineer Guide](docs/BACKEND_ENGINEER_GUIDE.md)** - Complete technical documentation
- **[Frontend Integration Guide](docs/FRONTEND_INTEGRATION_GUIDE.md)** - API integration for frontend
- **[Database DDL Guide](docs/DATABASE_DDL.md)** - Database schema and setup

## API Response Format

### Success Response
```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  ...
}
```

### Error Response
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-03-11T10:30:00",
  "validationErrors": {
    "firstName": "First name is required"
  }
}
```

## Security

- All endpoints (except `/api/auth/login`) require JWT authentication
- Role-based access control using `@PreAuthorize`
- BCrypt password encoding
- CORS enabled for frontend integration

## Development

### Run Tests
```bash
./gradlew test
```

### Check Code Quality
```bash
./gradlew check
```

## License

Proprietary - CuraMatrix Healthcare Solutions

## Support

For issues and questions, contact: support@curamatrix.com
