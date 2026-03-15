# Backend Engineer Guide

## Architecture Overview

```
Client Request
    │
    ▼
TenantInterceptor (extract tenantKey from JWT → set TenantContext)
    │
    ▼
JwtAuthenticationFilter (validate JWT → set SecurityContext)
    │
    ▼
Controller → Service → Repository → MySQL
    │
    ▼
TenantAwareEntity (@PrePersist auto-sets tenant_id)
```

---

## Multi-Tenant Architecture

Every hospital is a **Tenant**. Data is isolated by `tenant_id` on every table.

### How it works

1. On login, JWT is generated with `tenantId` and `tenantKey` embedded as claims
2. `TenantInterceptor` extracts `tenantKey` from JWT on every request and sets it in `TenantContext` (ThreadLocal)
3. All entities extend `TenantAwareEntity` which auto-sets `tenant_id` via `@PrePersist`
4. All repository queries filter by `tenant_id` from `TenantContext`

### TenantContext
```java
// Set by TenantInterceptor
TenantContext.setTenantId(tenantId);

// Used in repositories
Long tenantId = TenantContext.getTenantId();

// Always cleared after request
TenantContext.clear();
```

### TenantAwareEntity
```java
@MappedSuperclass
public abstract class TenantAwareEntity {
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    public void prePersist() {
        this.tenantId = TenantContext.getTenantId();
    }
}
```

---

## Project Structure

```
src/main/java/com/curamatrix/hsm/
├── config/
│   ├── JwtUtil.java               JWT generation and validation
│   ├── JwtAuthenticationFilter.java  JWT filter for every request
│   ├── SecurityConfig.java        Spring Security configuration
│   ├── TenantInterceptor.java     Extracts tenant from JWT
│   └── WebMvcConfig.java          Registers interceptors
├── context/
│   └── TenantContext.java         ThreadLocal tenant storage
├── controller/                    REST controllers
├── service/                       Business logic
├── repository/                    JPA repositories
├── entity/                        JPA entities
├── dto/
│   ├── request/                   Incoming request DTOs
│   └── response/                  Outgoing response DTOs
├── enums/                         All enums
└── exception/                     Custom exceptions
```

---

## Entities

| Entity | Table | Tenant Isolated |
|---|---|---|
| Tenant | tenants | No (platform level) |
| User | users | Yes |
| Doctor | doctors | No (linked via User) |
| Receptionist | receptionists | No (linked via User) |
| Department | departments | Yes |
| Patient | patients | Yes |
| Appointment | appointments | Yes |
| Diagnosis | diagnoses | Yes |
| Prescription | prescriptions | No (linked via Diagnosis) |
| Medicine | medicines | No (shared across tenants) |
| MedicineInventory | medicine_inventory | Yes |
| Billing | billings | Yes |
| BillingItem | billing_items | No (linked via Billing) |

---

## JWT Token

Token contains:
```json
{
  "sub": "user@email.com",
  "tenantId": 1,
  "tenantKey": "HOSPITAL_001",
  "iat": 1710000000,
  "exp": 1710086400
}
```

Expiry: 24 hours (configurable via `jwt.expiration` in application.yml)

---

## Roles and Permissions

| Role | Scope | Permissions |
|---|---|---|
| `ROLE_SUPER_ADMIN` | Platform | Manage all tenants |
| `ROLE_ADMIN` | Tenant | Manage users within hospital |
| `ROLE_DOCTOR` | Tenant | View queue, create diagnosis, prescriptions |
| `ROLE_RECEPTIONIST` | Tenant | Register patients, book appointments |

---

## Key Services

### AuthService
- Validates tenant exists and is active
- Checks subscription expiry
- Authenticates user credentials
- Verifies user belongs to the tenant
- Generates JWT with tenant claims

### TenantManagementService
- Register new hospital (creates tenant + admin user)
- Suspend / activate tenants
- Get tenant stats (user count, patient count)

### PatientService
- Register patient (auto-sets tenant from context)
- Search patients by name/phone within tenant
- Pagination support

### AppointmentService
- Book scheduled appointments
- Create walk-in appointments with auto token number
- Get available slots for a doctor on a date
- Get today's queue for a doctor

### DiagnosisService
- Create diagnosis linked to appointment
- One diagnosis per appointment (unique constraint)

### PrescriptionService
- Batch add prescriptions to a diagnosis
- Linked to medicines from shared medicine table

---

## Exception Handling

| Exception | HTTP Status | When |
|---|---|---|
| `TenantNotFoundException` | 404 | Invalid tenantKey on login |
| `SubscriptionExpiredException` | 403 | Tenant subscription expired |
| `QuotaExceededException` | 429 | Max users/patients limit reached |
| `UsernameNotFoundException` | 401 | User not found |
| `RuntimeException` | 400/500 | General errors |

---

## Adding a New Endpoint

1. Add method to Controller with `@PreAuthorize`
2. Add business logic to Service
3. Add query to Repository (always filter by `TenantContext.getTenantId()`)
4. Add request/response DTOs
5. Test via Swagger UI at `/swagger-ui.html`

---

## CI/CD Pipeline

```
Push to main
    │
    ▼
GitHub Actions
    ├── test (JUnit)
    ├── docker build + push to GHCR
    └── SSH deploy to EC2 (43.204.168.146)
```

Image: `ghcr.io/dejavu-vision/curamatrix-hsm:latest`

Logs on EC2: `/home/ec2-user/hsm-logs/hsm-YYYY-MM-DD.log`
