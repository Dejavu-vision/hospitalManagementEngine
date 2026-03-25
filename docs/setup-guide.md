# Setup Guide

## Prerequisites

- Java 17
- MySQL 8.0
- Docker (optional for local)
- Gradle 8.x

---

## Option 1 — Run Locally (without Docker)

### 1. Clone the repo
```bash
git clone https://github.com/Dejavu-vision/hospitalManagementEngine.git
cd hospitalManagementEngine
```

### 2. Create the database
```sql
CREATE DATABASE hospitalsystems;
CREATE USER 'backenduser'@'localhost' IDENTIFIED BY 'backenduser';
GRANT ALL PRIVILEGES ON hospitalsystems.* TO 'backenduser'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Run the schema
```bash
mysql -u backenduser -p hospitalsystems < docs/schema.sql
```

### 4. Configure application.yml
Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hospitalsystems
    username: backenduser
    password: backenduser
```

### 5. Run the app
```bash
./gradlew bootRun
```

App starts at: http://localhost:8080

---

## Option 2 — Run with Docker Compose

```bash
docker-compose up -d
```

This starts both MySQL and the app together. App at http://localhost:8080.

---

## Verify It's Running

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

Swagger UI: http://localhost:8080/swagger-ui.html

---

## Default Users (from seed data)

| Role | Email | Password | Tenant Key |
|---|---|---|---|
| Super Admin | superadmin@curamatrix.com | admin123 | PLATFORM |
| Admin | admin@hospital1.com | admin123 | HOSPITAL_001 |
| Doctor | doctor@hospital1.com | doctor123 | HOSPITAL_001 |
| Receptionist | reception@hospital1.com | reception123 | HOSPITAL_001 |

---

## Environment Variables (Production)

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JWT_SECRET` | JWT signing key (64+ chars) |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` |
