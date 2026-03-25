# Tenant Guide — Multi-Hospital SaaS

## What is a Tenant?

A tenant is a hospital on the CuraMatrix platform. Each hospital gets its own isolated environment — users, patients, appointments, and all data are completely separate from other hospitals.

---

## Tenant Lifecycle

```
Super Admin registers hospital
        │
        ▼
Tenant created + Admin user auto-created
        │
        ▼
Hospital Admin logs in with tenantKey
        │
        ▼
Admin creates doctors and receptionists
        │
        ▼
Hospital is operational
        │
        ▼
Subscription expires → logins blocked
        │
        ▼
Super Admin renews → hospital active again
        │
        ▼
Super Admin suspends → all logins blocked
```

---

## Tenant Fields

| Field | Description | Example |
|---|---|---|
| `tenantKey` | Unique identifier for the hospital | `apollo-mumbai` |
| `hospitalName` | Display name | `Apollo Hospital Mumbai` |
| `subscriptionPlan` | Plan tier | `BASIC / STANDARD / PROFESSIONAL / ENTERPRISE` |
| `subscriptionStart` | Plan start date | `2026-01-01` |
| `subscriptionEnd` | Plan expiry date | `2027-01-01` |
| `isActive` | Whether hospital can log in | `true / false` |
| `maxUsers` | Max staff accounts allowed | `50` |
| `maxPatients` | Max patient records allowed | `10000` |
| `contactEmail` | Hospital contact email | `admin@apollo.com` |
| `contactPhone` | Hospital contact phone | `9876543210` |
| `address` | Hospital address | `Mumbai, Maharashtra` |
| `logo` | URL to hospital logo | `https://...` |
| `settings` | Custom JSON config per hospital | `{}` |

---

## Subscription Plans

| Plan | Max Users | Max Patients | Recommended For |
|---|---|---|---|
| BASIC | 10 | 1,000 | Small clinics |
| STANDARD | 50 | 10,000 | Mid-size hospitals |
| PROFESSIONAL | 100 | 50,000 | Large hospitals |
| ENTERPRISE | Unlimited | Unlimited | Hospital chains |

---

## Super Admin API — Tenant Management

All endpoints require Super Admin JWT token.

Login as Super Admin:
```json
POST /api/auth/login
{
  "email": "superadmin@curamatrix.com",
  "password": "admin123",
  "tenantKey": "PLATFORM"
}
```

---

### Register a New Hospital

**POST** `/api/super-admin/tenants`

```json
{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PROFESSIONAL",
  "subscriptionStart": "2026-01-01",
  "subscriptionEnd": "2027-01-01",
  "contactEmail": "admin@apollo.com",
  "contactPhone": "9876543210",
  "address": "Andheri West, Mumbai, Maharashtra",
  "logo": "https://cdn.example.com/apollo-logo.png",
  "adminFullName": "Rajesh Kumar",
  "adminEmail": "rajesh@apollo.com",
  "adminPassword": "securepassword123",
  "adminPhone": "9876543211"
}
```

Response:
```json
{
  "id": 2,
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "subscriptionPlan": "PROFESSIONAL",
  "subscriptionStart": "2026-01-01",
  "subscriptionEnd": "2027-01-01",
  "isActive": true,
  "maxUsers": 100,
  "maxPatients": 50000,
  "contactEmail": "admin@apollo.com",
  "createdAt": "2026-03-16T10:00:00"
}
```

This also auto-creates the Admin user with `ROLE_ADMIN` for that hospital.

---

### Get All Tenants

**GET** `/api/super-admin/tenants`

```json
[
  {
    "id": 1,
    "tenantKey": "HOSPITAL_001",
    "hospitalName": "City Hospital",
    "subscriptionPlan": "STANDARD",
    "isActive": true,
    "subscriptionEnd": "2027-12-31"
  },
  {
    "id": 2,
    "tenantKey": "apollo-mumbai",
    "hospitalName": "Apollo Hospital Mumbai",
    "subscriptionPlan": "PROFESSIONAL",
    "isActive": true,
    "subscriptionEnd": "2027-01-01"
  }
]
```

---

### Get Tenant by ID

**GET** `/api/super-admin/tenants/{id}`

---

### Update Tenant

**PUT** `/api/super-admin/tenants/{id}`

```json
{
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai - Updated",
  "subscriptionPlan": "ENTERPRISE",
  "subscriptionStart": "2026-01-01",
  "subscriptionEnd": "2028-01-01",
  "contactEmail": "admin@apollo.com",
  "contactPhone": "9876543210",
  "adminFullName": "Rajesh Kumar",
  "adminEmail": "rajesh@apollo.com",
  "adminPassword": "newpassword123"
}
```

---

### Suspend a Hospital

Blocks all logins for that hospital immediately.

**PUT** `/api/super-admin/tenants/{id}/suspend`

After this, any login attempt from that hospital returns:
```json
{ "error": "Hospital account is suspended. Please contact support." }
```

---

### Activate a Hospital

Re-enables logins for a suspended hospital.

**PUT** `/api/super-admin/tenants/{id}/activate`

---

### Get Tenant Stats

**GET** `/api/super-admin/tenants/{id}/stats`

Returns usage stats for the hospital:
```json
{
  "tenantId": 1,
  "hospitalName": "City Hospital",
  "totalUsers": 12,
  "totalPatients": 340,
  "maxUsers": 50,
  "maxPatients": 10000,
  "usersUsagePercent": 24,
  "patientsUsagePercent": 3.4
}
```

---

## Hospital Admin — First Login Flow

After a hospital is registered by Super Admin:

1. Admin logs in with their credentials and the hospital's `tenantKey`
```json
POST /api/auth/login
{
  "email": "rajesh@apollo.com",
  "password": "securepassword123",
  "tenantKey": "apollo-mumbai"
}
```

2. Admin creates departments (if not seeded)

3. Admin creates doctors:
```json
POST /api/admin/users
{
  "fullName": "Dr. Priya Mehta",
  "email": "dr.priya@apollo.com",
  "password": "doctor123",
  "phone": "9876543212",
  "role": "ROLE_DOCTOR",
  "specialization": "Neurology",
  "licenseNumber": "MH-67890",
  "qualification": "MBBS, DM Neurology",
  "experienceYears": 8,
  "consultationFee": 800.00,
  "departmentId": 2
}
```

4. Admin creates receptionists:
```json
POST /api/admin/users
{
  "fullName": "Sneha Patil",
  "email": "sneha@apollo.com",
  "password": "reception123",
  "phone": "9876543213",
  "role": "ROLE_RECEPTIONIST",
  "shift": "MORNING"
}
```

---

## Tenant Isolation — How It Works

Every table that holds hospital data has a `tenant_id` column:

```
patients         → tenant_id
appointments     → tenant_id
diagnoses        → tenant_id
users            → tenant_id
departments      → tenant_id
medicine_inventory → tenant_id
billings         → tenant_id
```

When a user logs in, their JWT contains `tenantId`. Every API request automatically filters data by that `tenantId`. A doctor from Apollo Mumbai can never see patients from City Hospital — even if they somehow got a valid JWT.

---

## Subscription Expiry Behaviour

When `subscriptionEnd` date passes:

- All login attempts return: `"Subscription has expired. Please renew to continue."`
- Existing sessions (valid JWT) continue to work until token expires (24h)
- Super Admin can update `subscriptionEnd` to renew

---

## tenantKey Rules

- Must be lowercase
- Only letters, numbers, and hyphens allowed
- Must be unique across the platform
- Cannot be changed after creation
- Used in every login request

Valid: `apollo-mumbai`, `city-hospital-01`, `fortis-delhi`
Invalid: `Apollo Mumbai`, `CITY_HOSPITAL`, `fortis.delhi`

---

## Multi-Hospital Chain Setup

For a hospital chain (e.g. Apollo with 3 branches), register each branch as a separate tenant:

```
apollo-mumbai     → Apollo Hospital Mumbai
apollo-delhi      → Apollo Hospital Delhi  
apollo-bangalore  → Apollo Hospital Bangalore
```

Each branch has its own admin, doctors, patients, and data. There is no cross-branch data sharing at the API level.
