# CuraMatrix Hospital Management System (HSM)

A comprehensive Hospital Management System backend built with Spring Boot 3.5.11, providing REST APIs for patient management, appointments, diagnosis, prescriptions, and billing.

## Features

- **JWT Authentication** - Secure role-based access control
- **Patient Management** - Register and manage patient records
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

### Authentication
- `POST /api/auth/login` - Login and get JWT token

### Patients (Receptionist)
- `POST /api/patients` - Register new patient
- `GET /api/patients` - Search patients (with pagination)
- `GET /api/patients/{id}` - Get patient details
- `PUT /api/patients/{id}` - Update patient

### Appointments (Receptionist)
- `POST /api/appointments` - Book scheduled appointment
- `POST /api/appointments/walk-in` - Create walk-in appointment
- `GET /api/appointments` - List appointments (with filters)
- `GET /api/appointments/doctor/{id}/slots` - Get available time slots
- `PUT /api/appointments/{id}/status` - Update appointment status

### Doctor Queue (Doctor)
- `GET /api/appointments/doctor/{id}/today` - Get today's patient queue

### Diagnosis (Doctor)
- `POST /api/diagnoses` - Create diagnosis
- `GET /api/diagnoses/{id}` - Get diagnosis details
- `GET /api/diagnoses/appointment/{id}` - Get diagnosis by appointment
- `PUT /api/diagnoses/{id}` - Update diagnosis

### Prescriptions (Doctor)
- `POST /api/prescriptions` - Add prescriptions (batch)
- `GET /api/prescriptions/diagnosis/{id}` - Get prescriptions by diagnosis

### Medicine Search (Doctor/Admin)
- `GET /api/medicines/search?query=parac` - Search medicines (autocomplete)

### Departments (Admin/Receptionist)
- `GET /api/departments` - List all active departments
- `GET /api/departments/{id}` - Get department details

## Testing with Postman

1. Import collection: `docs/CuraMatrix_HSM.postman_collection.json`
2. Import environment: `docs/CuraMatrix_HSM.postman_environment.json`
3. Login first (token auto-saves to environment)
4. Test other endpoints

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

## Documentation

- **Backend Guide**: `docs/BACKEND_ENGINEER_GUIDE.md`
- **Frontend Integration**: `docs/FRONTEND_INTEGRATION_GUIDE.md`
- **Database DDL**: `docs/DATABASE_DDL.md`
- **Postman Setup**: `docs/POSTMAN_SETUP.md`

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
