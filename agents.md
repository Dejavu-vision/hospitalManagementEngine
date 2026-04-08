# CuraMatrix Hospital Management System — Agent Guidelines

> This document describes the architecture, conventions, and rules for safely modifying the **CuraMatrix HSM** codebase. It is grounded in the actual repository structure — no assumptions.

---

## 1. Overview

CuraMatrix HSM is a **multi-tenant SaaS Hospital Management System** built for the Indian healthcare market. It manages patients, appointments, billing, prescriptions, pharmacy, doctor consultations, and administrative operations for **multiple hospitals** from a single codebase.

### Key Responsibilities
- **Multi-Tenant Isolation**: Every hospital is a `Tenant`. All data is scoped to `tenant_id`.
- **Role-Based Access Control (RBAC)**: Roles (`ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_RECEPTIONIST`) with page-level access.
- **Clinical Workflow**: Patient registration → walk-in/appointment → consultation → diagnosis → prescription.
- **Billing & Case Papers**: Configurable, patient-centric billing with case paper validity tracking.
- **Super Admin Panel**: Cross-tenant hospital onboarding and subscription management.

---

## 2. Repositories

| Repository | Path | Purpose |
|---|---|---|
| **Backend** | `hospitalManagementEngine/` | Spring Boot REST API |
| **Frontend** | `HospitalManagment/hospital-management/` | React SPA |

---

## 3. Backend — Project Structure

```
src/main/java/com/curamatrix/hsm/
├── HsmApplication.java            # Spring Boot entry point
├── config/                         # Security, JWT, CORS, interceptors
│   ├── SecurityConfig.java         # Spring Security filter chain (stateless JWT)
│   ├── JwtAuthenticationFilter.java# Extract & validate JWT on every request
│   ├── JwtUtil.java                # Token generation & claims extraction
│   ├── TenantInterceptor.java      # Sets TenantContext from JWT claims
│   ├── WebMvcConfig.java           # Registers TenantInterceptor
│   ├── RateLimitingFilter.java     # Brute-force protection
│   └── OpenApiConfig.java          # Swagger / OpenAPI docs
├── context/
│   └── TenantContext.java          # ThreadLocal tenant ID/key (CRITICAL)
├── controller/                     # REST controllers (17 files)
├── service/                        # Business logic (21 files)
├── repository/                     # Spring Data JPA repositories (22 files)
├── entity/                         # JPA entities (25 files)
├── dto/
│   ├── request/                    # Incoming DTOs (19 files)
│   └── response/                   # Outgoing DTOs (21 files)
├── enums/                          # Domain enums (12 files)
├── exception/                      # Custom exceptions + GlobalExceptionHandler
├── helper/                         # Mappers (e.g., MedicineMapper.java)
└── util/                           # Utility classes (currently unused — see DO NOT section)
```

### Key Configuration Files
| File | Purpose |
|---|---|
| `build.gradle` | Gradle build config, dependencies |
| `src/main/resources/application.yml` | Datasource, JWT secret, server port, actuator, swagger |
| `Dockerfile` | Production Docker image |
| `docker-compose.yml` | Local dev with MySQL |
| `docker-compose.prod.yml` | Production deployment |

---

## 4. Tech Stack

### Backend
| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.11 |
| Build Tool | **Gradle** (`./gradlew`) |
| Database | MySQL 8 (via `mysql-connector-j`) |
| ORM | Spring Data JPA / Hibernate 6 |
| Authentication | JWT (jjwt 0.12.6) — stateless |
| Password Hashing | BCrypt |
| Validation | `spring-boot-starter-validation` (Jakarta) |
| API Docs | SpringDoc OpenAPI (`/swagger-ui.html`) |
| Monitoring | Spring Actuator (`/actuator/health`, `/actuator/info`) |
| Code Generation | Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) |
| DDL Strategy | `hibernate.ddl-auto: update` (auto schema evolution) |

### Frontend
| Component | Technology |
|---|---|
| Framework | React 19 (Vite 8) |
| State Management | Redux Toolkit + `react-redux` |
| Routing | React Router 7 |
| Styling | Tailwind CSS 4 + custom CSS |
| HTTP Client | Native `fetch` via `apiConfig.js` |
| Build Tool | Vite (`npm run dev`) |

---

## 5. Architecture

### 5.1 Backend Layers

```
Client → JwtAuthenticationFilter → TenantInterceptor → Controller → Service → Repository → MySQL
```

**Strict flow**: `Controller → Service → Repository`. Controllers NEVER call repositories directly, except for simple read-only queries where a dedicated service method would be trivial.

### 5.2 Multi-Tenancy (CRITICAL)

Every request passes through:

1. **`JwtAuthenticationFilter`** — authenticates the user from the `Authorization: Bearer <token>` header.
2. **`TenantInterceptor`** — extracts `tenantId` and `tenantKey` from JWT claims and calls `TenantContext.setTenantId()`.
3. **`TenantContext`** — `ThreadLocal<Long>` that stores the current tenant ID for the request lifecycle.

**Entity-level enforcement:**

```java
@MappedSuperclass
public abstract class TenantAwareEntity {
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist @PreUpdate
    public void setTenantIdFromContext() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getTenantId(); // auto-set
        }
    }
}
```

All domain entities extend `TenantAwareEntity` (except: `Doctor`, `Receptionist`, `BillingItem`, `Role`, `UiPage`, `EmployeeIdSequence`, `WalkInTokenSequence`).

### 5.3 RBAC System

- **`Role`** → `RoleName` enum (`ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_RECEPTIONIST`)
- **`UiPage`** → defines available UI pages/features
- **`RolePage`** → maps roles to default page access
- **`UserPage`** → per-user page overrides
- **`UserAccessAudit`** → logs changes to user permissions
- **`AccessControlService`** computes effective page access: `rolePages + userPages`

### 5.4 Frontend Architecture

```
src/
├── config/           # apiConfig.js (fetch wrapper), apiEndpoints.js
├── services/         # Feature-specific API modules (billingApi.js, etc.)
├── Pages/            # Feature-based modules
│   ├── Login/        # authSlice, authThunk, authService, Login.jsx
│   ├── Dashboard/    # dashboardSlice, dashboardService, Dashboard.jsx
│   ├── Patients/     # patientsSlice, patientsThunks, Patients.jsx
│   ├── Receptionist/ # PatientRegister, BookAppointment, QueueManagement, etc.
│   ├── Doctor/       # ConsultationDashboard, PatientConsultation, etc.
│   ├── Billing/      # Billing.jsx, CasePaperPrint.jsx
│   ├── Pharmacy/     # pharmacySlice, pharmacyThunks, Pharmacy.jsx
│   └── Admin/        # Reports, Settings, Roles, Pages, ServiceManagement, etc.
├── Components/       # Shared UI components (Sidebar, ModalManager, etc.)
├── rbac/             # Frontend RBAC helpers
├── store/            # Redux store configuration
├── theme/            # Theme constants
└── App.jsx           # Route definitions and layout
```

**Pattern**: Each feature module contains `{Feature}Service.js` (API calls), `{Feature}Slice.js` (Redux state), `{Feature}Thunks.js` (async actions), and `{Feature}.jsx` (page component).

---

## 6. Coding Conventions

### 6.1 Backend

| Convention | Example |
|---|---|
| Package structure | `com.curamatrix.hsm.{layer}` |
| Entity naming | PascalCase, singular (`Patient`, `Billing`) |
| Table naming | snake_case, plural (`patients`, `billings`) |
| Controller paths | `/api/{resource}` (e.g., `/api/patients`, `/api/billing`) |
| Service classes | `@Service` + `@RequiredArgsConstructor` + `@Slf4j` |
| Repository classes | Extend `JpaRepository<Entity, Long>` |
| DTOs | Separate `request/` and `response/` packages under `dto/` |
| Enum persistence | `@Enumerated(EnumType.STRING)` with `@Column(length = 50)` |
| Constructor injection | Via Lombok `@RequiredArgsConstructor` (never `@Autowired`) |
| Transactions | `@Transactional` on service methods (not controllers) |
| Builders | Lombok `@Builder` on entities and DTOs |

### 6.2 Frontend

| Convention | Example |
|---|---|
| Component files | PascalCase `.jsx` (`PatientRegister.jsx`) |
| Service files | camelCase `.js` (`billingApi.js`) |
| State management | Redux Toolkit slices + thunks |
| API calls | `apiRequest()` from `config/apiConfig.js` |
| Inline styles | Heavily used in page components (not Tailwind utility classes) |
| Feature path | Group by feature under `Pages/{FeatureName}/` |

---

## 7. API Conventions

### Endpoint Patterns

```
GET    /api/{resource}               → List (paginated or all)
GET    /api/{resource}/{id}          → Get by ID
POST   /api/{resource}               → Create
PUT    /api/{resource}/{id}          → Full update
PATCH  /api/{resource}/{id}/status   → Status transitions
DELETE /api/{resource}/{id}          → Delete/deactivate
```

### Authentication

- All endpoints require `Authorization: Bearer <jwt_token>` except `/api/auth/**`
- JWT contains: `userId`, `tenantId`, `tenantKey`, `authorities`, `pageKeys`

### Error Response Format (from GlobalExceptionHandler)

```json
{
  "timestamp": "2026-04-04T13:25:12.345Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Descriptive error message",
  "path": "/api/patients"
}
```

### Custom Exception → HTTP Status Mapping

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `InvalidStateTransitionException` | 422 |
| `QuotaExceededException` | 403 |
| `SubscriptionExpiredException` | 403 |
| `UserDeactivatedException` | 403 |
| `BruteForceProtectionException` | 429 |
| `IllegalArgumentException` | 400 |

---

## 8. Database Design

### Core Entities & Relationships

```
Tenant (1) ←── (M) User ←── (1) Doctor / Receptionist
Tenant (1) ←── (M) Patient
Tenant (1) ←── (M) Department ←── (M) Doctor
Patient (1) ←── (M) Appointment → Doctor
Appointment (1) ←── (1) Billing ←── (M) BillingItem
Patient (1) ←── (M) PatientRegistration → Billing
Patient (1) ←── (M) Diagnosis
Diagnosis (1) ←── (M) Prescription
Tenant (1) ←── (M) HospitalService (configurable service pricing)
Tenant (1) ←── (M) Medicine / MedicineInventory
```

### Key Design Decisions

- **`TenantAwareEntity`** is the base class for tenant-scoped entities. Tenant ID is auto-set from `TenantContext` on `@PrePersist`.
- **`Doctor`** does NOT extend `TenantAwareEntity`. It has no direct `tenant_id` column. Tenant scoping is via `User.tenantId` (joined through `doctor.user_id → users.id`). Queries use native SQL with `JOIN users u ON d.user_id = u.id WHERE u.tenant_id = :tenantId`.
- **`BillingItem`** is not tenant-aware — it inherits tenant scope from its parent `Billing`.
- **Enums stored as strings**: Use `@Enumerated(EnumType.STRING)` with explicit `@Column(length = 50)`.
- **Composite unique constraints** for multi-tenancy: e.g., `@UniqueConstraint(columnNames = {"service_code", "tenant_id"})`.

---

## 9. How to Run

### Backend

```bash
cd hospitalManagementEngine

# Requires Java 17+
./gradlew bootRun

# Build JAR
./gradlew build

# Run tests
./gradlew test
```

> **Note**: `./gradlew` requires Java 17. If terminal defaults to Java 11, set `JAVA_HOME` first.

### Frontend

```bash
cd HospitalManagment/hospital-management

npm install
npm run dev    # Vite dev server (default: http://localhost:5173)
npm run build  # Production build
```

### Docker (Full Stack)

```bash
cd hospitalManagementEngine
docker-compose up -d
```

---

## 10. Testing Strategy

| Tool | Purpose |
|---|---|
| JUnit 5 | Unit tests (`./gradlew test`) |
| Spring Boot Test | Integration tests (`@SpringBootTest`) |
| Spring Security Test | Auth/security test utilities |
| JUnit Platform Launcher | Test runner |

Tests are in `src/test/java/com/curamatrix/hsm/`.

---

## 11. Agent Guidelines — How to Modify This Repo

### Adding a New Entity

1. Create the entity in `entity/` extending `TenantAwareEntity`
2. Add `@Entity`, `@Table`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
3. Use `@Column(name = "snake_case")` for every field
4. Use `@Enumerated(EnumType.STRING)` with `@Column(length = 50)` for enums
5. **NEVER** use `.tenantId()` in builders — the `tenantId` field is inherited and NOT available in Lombok builders. Instead:
   ```java
   MyEntity entity = MyEntity.builder().build();
   entity.setTenantId(tenantId); // MUST use setter
   ```
6. Create the repository in `repository/` extending `JpaRepository<Entity, Long>`

### Adding a New REST Endpoint

1. Create or update a controller in `controller/`
2. Use `@RestController`, `@RequestMapping("/api/{resource}")`, `@RequiredArgsConstructor`
3. Get tenant ID via `TenantContext.getTenantId()` — import from `com.curamatrix.hsm.context.TenantContext`
4. Delegate to a service — controllers should not contain business logic
5. Return `ResponseEntity<T>` for all endpoints

### Adding a New Service

1. Create in `service/`
2. Annotate with `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
3. Use `@Transactional` for write operations, `@Transactional(readOnly = true)` for reads
4. Inject repositories and other services via `private final` fields (constructor injection via Lombok)

### Adding Tenant-Scoped Queries

- For entities extending `TenantAwareEntity`: Use Spring Data derived queries like `findByIdAndTenantId(Long id, Long tenantId)`
- For entities NOT extending `TenantAwareEntity` (e.g., `Doctor`): Use `@Query(nativeQuery = true)` with explicit JOIN to `users` table:
  ```java
  @Query(value = "SELECT d.* FROM doctors d JOIN users u ON d.user_id = u.id WHERE u.tenant_id = :tenantId", nativeQuery = true)
  List<Doctor> findByTenantId(@Param("tenantId") Long tenantId);
  ```

### Adding a Frontend Page

1. Create a folder under `Pages/{FeatureName}/`
2. Add page component (`Feature.jsx`), service (`featureService.js`), and optionally Redux slice/thunks
3. Register the route in `App.jsx`
4. Use `apiRequest()` from `config/apiConfig.js` for HTTP calls
5. Follow pattern: stateful function component with inline styles

### Adding Custom Exceptions

1. Create the exception in `exception/` extending `RuntimeException`
2. Add a handler method in `GlobalExceptionHandler.java` mapping to the appropriate HTTP status
3. Use the `build()` helper for consistent error response format

---

## 12. DO NOT — Anti-Patterns to Avoid

### ❌ NEVER import from `com.curamatrix.hsm.util.TenantContext`
> The correct import is `com.curamatrix.hsm.context.TenantContext`. The `util/` package exists but does NOT contain `TenantContext`.

### ❌ NEVER call `TenantContext.getCurrentTenantId()`
> The correct method is `TenantContext.getTenantId()`.

### ❌ NEVER use `.tenantId()` in Lombok builders for entities extending `TenantAwareEntity`
> Lombok `@Builder` does NOT inherit fields from `@MappedSuperclass` parents. Always use `entity.setTenantId(id)` after building.

### ❌ NEVER use `Map.of()` with potentially null values
> `Map.of()` throws `NullPointerException` for null values. Use `HashMap` instead.

### ❌ NEVER use a global unique constraint on a field that should be unique per tenant
> Multi-tenant data requires composite unique constraints: `@UniqueConstraint(columnNames = {"field", "tenant_id"})`.

### ❌ NEVER call `Doctor.getFullName()` directly
> `Doctor` doesn't have `fullName`. Use `doctor.getUser().getFullName()`.

### ❌ NEVER create duplicate `@RequiredArgsConstructor` fields of the same type
> This causes Spring injection ambiguity (e.g., two `HospitalServiceRepository` fields).

### ❌ NEVER use `@Autowired` — use constructor injection via `@RequiredArgsConstructor`

### ❌ NEVER use MySQL `ENUM` column type for JPA `@Enumerated(EnumType.STRING)`
> Always specify `@Column(length = 50)` to force `VARCHAR` in MySQL. Otherwise, adding new enum values causes "Data truncated" errors.

### ❌ NEVER reference `SecurityUtils` — this class does NOT exist
> Use `TenantContext.getTenantId()` for tenant ID. Use `SecurityContextHolder` for the current user.

### ❌ NEVER use `@Builder` with inherited Lombok fields without `@SuperBuilder`
> If you need builder support for inherited fields, use `@SuperBuilder` on both parent and child. Currently the codebase uses `@Builder` + manual `setTenantId()` calls.

---

## 13. Quick Reference — Common Import Paths

```java
// Tenant
import com.curamatrix.hsm.context.TenantContext;     // ✅ CORRECT

// Entities — wildcard import
import com.curamatrix.hsm.entity.*;

// Enums
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.RoleName;

// Exceptions
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.exception.DuplicateResourceException;
```

---

## 14. Environment & Deployment

| Environment | Database URL | JWT Secret |
|---|---|---|
| Local Dev | `jdbc:mysql://43.204.168.146:3306/hospitalsystems` | Hardcoded in `application.yml` |
| Production | `${SPRING_DATASOURCE_URL}` env var | `${JWT_SECRET}` env var |

- **Server Port**: 8080
- **Frontend Dev Port**: 5173 (Vite default)
- **API Base URL**: Configured in frontend via `VITE_API_BASE_URL` env var, defaults to `http://localhost:8080/api`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

*Generated from actual repository analysis — CuraMatrix HSM v0.0.1-SNAPSHOT*

### ❌ NEVER assume `LocalDateTime` serializes to ISO-8601 string by default in JSON Maps
> Without Jackson configuration, `LocalDateTime` in a `Map<String, Object>` serializes to an array `[2026, 4, 4, ...]` breaking JS `new Date()`. Manually call `.toString()` when putting dates into Maps.
