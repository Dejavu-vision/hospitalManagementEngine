# CuraMatrix Hospital Management System (HSM)
# Frontend Engineer -- API Integration Guide

**Version:** 1.0.0
**Date:** March 11, 2026
**Backend Base URL:** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**API Docs (JSON):** `http://localhost:8080/v3/api-docs`

---

## Table of Contents

1. [Overview](#1-overview)
2. [User Roles and Screens](#2-user-roles-and-screens)
3. [Authentication Flow](#3-authentication-flow)
4. [API Request/Response Conventions](#4-api-requestresponse-conventions)
5. [Error Handling](#5-error-handling)
6. [API Reference: Authentication](#6-api-reference-authentication)
7. [API Reference: Receptionist Module](#7-api-reference-receptionist-module)
8. [API Reference: Doctor Module](#8-api-reference-doctor-module)
9. [API Reference: Admin Module](#9-api-reference-admin-module)
10. [API Reference: Reports](#10-api-reference-reports)
11. [Enum Values Reference](#11-enum-values-reference)
12. [Pagination Guide](#12-pagination-guide)
13. [Suggested Screen Layouts](#13-suggested-screen-layouts)
14. [WebSocket / Real-time (Future)](#14-websocket--real-time-future)
15. [CORS Configuration](#15-cors-configuration)
16. [Quick Start Checklist](#16-quick-start-checklist)

---

## 1. Overview

CuraMatrix HSM is a Hospital Management System with three user roles. The backend is a Spring Boot REST API that communicates via JSON over HTTP. Authentication uses JWT (JSON Web Tokens).

**Key Points for Frontend:**
- All API endpoints are prefixed with `/api/`
- All requests (except login) require a JWT token in the `Authorization` header
- All request/response bodies are JSON (`Content-Type: application/json`)
- Dates use ISO format: `YYYY-MM-DD` (e.g., `2026-03-15`)
- Times use 24-hour format: `HH:mm` (e.g., `14:30`)
- Timestamps use ISO 8601: `YYYY-MM-DDTHH:mm:ss` (e.g., `2026-03-11T10:30:00`)

---

## 2. User Roles and Screens

### 2.1 Role-Based Access

| Role | Code | Description |
|------|------|-------------|
| Admin | `ROLE_ADMIN` | Full system management |
| Doctor | `ROLE_DOCTOR` | Patient diagnosis and prescriptions |
| Receptionist | `ROLE_RECEPTIONIST` | Patient registration, appointments, billing |

### 2.2 Suggested Screens Per Role

#### Receptionist Screens
| Screen | Description | Primary APIs |
|--------|-------------|-------------|
| **Patient Registration** | Form to register new patient | `POST /api/patients` |
| **Patient Search** | Search and list patients | `GET /api/patients?search=` |
| **Patient Details** | View/edit patient info | `GET/PUT /api/patients/{id}` |
| **Book Appointment** | Schedule appointment with doctor | `POST /api/appointments` |
| **Walk-in Registration** | Generate walk-in token | `POST /api/appointments/walk-in` |
| **Appointment List** | View all appointments (filterable) | `GET /api/appointments` |
| **Doctor Slot Viewer** | Check available slots | `GET /api/appointments/doctor/{id}/slots` |
| **Billing** | Generate and manage invoices | `POST /api/billings`, `PUT /api/billings/{id}/payment` |
| **Billing History** | Patient billing history | `GET /api/billings/patient/{id}` |

#### Doctor Screens
| Screen | Description | Primary APIs |
|--------|-------------|-------------|
| **Today's Queue** | View today's appointments | `GET /api/appointments/doctor/{id}/today` |
| **Patient History** | View patient medical history | `GET /api/patients/{id}/history` |
| **Diagnosis Form** | Create/edit diagnosis | `POST/PUT /api/diagnoses` |
| **Prescription Form** | Add prescriptions with medicine search | `POST /api/prescriptions` |
| **Medicine Search** | Autocomplete medicine search | `GET /api/medicines/search?query=` |

#### Admin Screens
| Screen | Description | Primary APIs |
|--------|-------------|-------------|
| **Dashboard** | Summary stats and charts | `GET /api/reports/dashboard` |
| **User Management** | Create/edit/deactivate users | `POST/GET/PUT/DELETE /api/admin/users` |
| **Department Management** | Manage departments | `POST/GET/PUT /api/admin/departments` |
| **Medicine Management** | Add/edit medicines | `POST/PUT /api/medicines` |
| **Inventory Management** | Stock and expiry tracking | `GET/POST/PUT /api/inventory` |
| **Revenue Reports** | Revenue analytics | `GET /api/reports/revenue` |
| **Doctor Performance** | Doctor-wise stats | `GET /api/reports/doctors/performance` |

---

## 3. Authentication Flow

### 3.1 Login Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│ Login    │  POST   │  Backend │  Query  │  MySQL   │
│ Screen   │────────>│  /auth/  │────────>│  users   │
│          │         │  login   │         │  table   │
│          │<────────│         │<────────│          │
│ Store JWT│  200 OK │          │  User + │          │
│ in memory│  + JWT  │          │  Roles  │          │
└──────────┘         └──────────┘         └──────────┘
```

### 3.2 How to Store the Token

**Recommended:** Store JWT in memory (React state / Zustand / Redux) + `httpOnly` cookie for refresh.

**NOT Recommended:** `localStorage` (XSS vulnerable).

### 3.3 How to Send the Token

Every API request (except `/api/auth/login`) must include:

```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkb2N0b3JA...
```

### 3.4 Token Expiry Handling

- Access token expires in **24 hours** (`expiresIn` field in login response)
- When you receive a `401 Unauthorized` response, redirect to login
- Optionally implement token refresh: `POST /api/auth/refresh`

### 3.5 JavaScript/TypeScript Example

```typescript
// api.ts - Axios setup
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' }
});

// Add JWT to every request
api.interceptors.request.use((config) => {
  const token = getStoredToken(); // your token storage
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearToken();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

### 3.6 Role-Based Routing

After login, use the `role` field from the response to redirect:

```typescript
// After successful login
const { token, role, fullName, userId } = response.data;

switch (role) {
  case 'ROLE_RECEPTIONIST':
    navigate('/receptionist/dashboard');
    break;
  case 'ROLE_DOCTOR':
    navigate('/doctor/queue');
    break;
  case 'ROLE_ADMIN':
    navigate('/admin/dashboard');
    break;
}
```

---

## 4. API Request/Response Conventions

### 4.1 Base URL

```
http://localhost:8080/api
```

### 4.2 Common Headers

| Header | Value | Required |
|--------|-------|----------|
| `Content-Type` | `application/json` | For POST/PUT requests |
| `Authorization` | `Bearer <jwt_token>` | All except `/auth/login` |
| `Accept` | `application/json` | Optional (default) |

### 4.3 Success Response Wrapper

All successful responses follow this structure:

```typescript
// For single resource operations (POST, PUT, GET by ID)
interface ApiResponse<T> {
  success: boolean;       // always true for 2xx
  message: string;        // human-readable message
  data: T;                // the actual payload
  timestamp: string;      // ISO 8601 datetime
}

// For paginated lists (GET collections)
interface PagedResponse<T> {
  content: T[];           // array of items
  pageNumber: number;     // current page (0-based)
  pageSize: number;       // items per page
  totalElements: number;  // total items across all pages
  totalPages: number;     // total number of pages
  last: boolean;          // true if this is the last page
}
```

### 4.4 HTTP Methods

| Method | Usage | Request Body | Response Code |
|--------|-------|-------------|---------------|
| GET | Fetch data | None | 200 |
| POST | Create resource | JSON body | 201 |
| PUT | Update resource | JSON body | 200 |
| DELETE | Deactivate resource | None | 204 |

---

## 5. Error Handling

### 5.1 Error Response Structure

```typescript
interface ErrorResponse {
  status: number;                          // HTTP status code
  error: string;                           // Error type (e.g., "Not Found")
  message: string;                         // Human-readable message
  timestamp: string;                       // ISO 8601 datetime
  validationErrors?: Record<string, string>; // Field-level errors (400 only)
}
```

### 5.2 Error Examples

**401 Unauthorized (invalid/expired token):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token is expired or invalid",
  "timestamp": "2026-03-11T10:30:00"
}
```

**403 Forbidden (wrong role):**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "timestamp": "2026-03-11T10:30:00"
}
```

**400 Validation Error:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-03-11T10:30:00",
  "validationErrors": {
    "firstName": "First name is required",
    "phone": "Phone must be 10 digits",
    "dateOfBirth": "Date of birth is required"
  }
}
```

**404 Not Found:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Patient not found with id: 999",
  "timestamp": "2026-03-11T10:30:00"
}
```

**409 Conflict (slot already booked):**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Time slot 10:30 is already booked for Dr. Rajesh Kumar on 2026-03-15",
  "timestamp": "2026-03-11T10:30:00"
}
```

### 5.3 Frontend Error Handling Pattern

```typescript
try {
  const response = await api.post('/patients', patientData);
  showSuccess(response.data.message);
} catch (error) {
  if (error.response) {
    const { status, data } = error.response;

    switch (status) {
      case 400:
        // Show field-level validation errors
        if (data.validationErrors) {
          Object.entries(data.validationErrors).forEach(([field, msg]) => {
            setFieldError(field, msg as string);
          });
        } else {
          showError(data.message);
        }
        break;
      case 401:
        redirectToLogin();
        break;
      case 403:
        showError('You do not have permission for this action');
        break;
      case 404:
        showError(data.message);
        break;
      case 409:
        showError(data.message); // slot conflict, duplicate, etc.
        break;
      default:
        showError('Something went wrong. Please try again.');
    }
  }
}
```

---

## 6. API Reference: Authentication

### POST /api/auth/login

**Description:** Authenticate user and receive JWT token.

**Access:** Public (no token required)

**Request Body:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| email | string | Yes | Valid email format |
| password | string | Yes | Non-empty |

```json
{
  "email": "doctor@hospital.com",
  "password": "password123"
}
```

**Success Response (200):**

| Field | Type | Description |
|-------|------|-------------|
| token | string | JWT access token |
| tokenType | string | Always "Bearer" |
| userId | number | User's ID |
| fullName | string | User's display name |
| role | string | ROLE_ADMIN, ROLE_DOCTOR, or ROLE_RECEPTIONIST |
| expiresIn | number | Token validity in milliseconds |

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "userId": 5,
  "fullName": "Dr. Rajesh Kumar",
  "role": "ROLE_DOCTOR",
  "expiresIn": 86400000
}
```

**Error Response (401):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "timestamp": "2026-03-11T10:30:00"
}
```

---

### POST /api/auth/refresh

**Description:** Refresh an expiring token.

**Access:** Authenticated (any role)

**Headers:** `Authorization: Bearer <current_token>`

**Success Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...(new token)",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

---

## 7. API Reference: Receptionist Module

### 7.1 Patient APIs

#### POST /api/patients -- Register New Patient

**Access:** `ROLE_RECEPTIONIST`

**Request Body:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| firstName | string | Yes | Non-empty |
| lastName | string | Yes | Non-empty |
| dateOfBirth | string | Yes | Format: YYYY-MM-DD |
| gender | string | Yes | Enum: `MALE`, `FEMALE`, `OTHER` |
| phone | string | Yes | 10-digit number |
| email | string | No | Valid email format |
| address | string | No | |
| bloodGroup | string | No | Enum: see [Section 11](#11-enum-values-reference) |
| emergencyContactName | string | No | |
| emergencyContactPhone | string | No | |
| allergies | string | No | Free text |
| medicalHistory | string | No | Free text |

```json
{
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit@email.com",
  "address": "123 MG Road, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "9876543211",
  "allergies": "Penicillin",
  "medicalHistory": "Diabetes Type 2"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Patient registered successfully",
  "data": {
    "id": 101,
    "firstName": "Amit",
    "lastName": "Sharma",
    "dateOfBirth": "1990-05-15",
    "gender": "MALE",
    "phone": "9876543210",
    "email": "amit@email.com",
    "address": "123 MG Road, Mumbai",
    "bloodGroup": "B_POSITIVE",
    "emergencyContactName": "Priya Sharma",
    "emergencyContactPhone": "9876543211",
    "allergies": "Penicillin",
    "medicalHistory": "Diabetes Type 2",
    "registeredAt": "2026-03-11T10:30:00"
  },
  "timestamp": "2026-03-11T10:30:00"
}
```

---

#### GET /api/patients -- List Patients (Paginated + Searchable)

**Access:** `ROLE_RECEPTIONIST`, `ROLE_DOCTOR`

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | number | 0 | Page number (0-based) |
| size | number | 20 | Items per page |
| search | string | | Search by name or phone |
| sortBy | string | registeredAt | Sort field |
| sortDir | string | desc | `asc` or `desc` |

**Example:** `GET /api/patients?page=0&size=20&search=amit`

**Success Response (200):**
```json
{
  "content": [
    {
      "id": 101,
      "firstName": "Amit",
      "lastName": "Sharma",
      "phone": "9876543210",
      "gender": "MALE",
      "bloodGroup": "B_POSITIVE",
      "registeredAt": "2026-03-11T10:30:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

#### GET /api/patients/{id} -- Get Patient Details

**Access:** `ROLE_RECEPTIONIST`, `ROLE_DOCTOR`

**Path Params:** `id` (number) -- Patient ID

**Success Response (200):**
```json
{
  "id": 101,
  "firstName": "Amit",
  "lastName": "Sharma",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "phone": "9876543210",
  "email": "amit@email.com",
  "address": "123 MG Road, Mumbai",
  "bloodGroup": "B_POSITIVE",
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "9876543211",
  "allergies": "Penicillin",
  "medicalHistory": "Diabetes Type 2",
  "registeredAt": "2026-03-11T10:30:00"
}
```

---

#### PUT /api/patients/{id} -- Update Patient

**Access:** `ROLE_RECEPTIONIST`

**Request Body:** Same as POST (all fields optional, only provided fields are updated)

---

#### GET /api/patients/{id}/history -- Patient Medical History

**Access:** `ROLE_DOCTOR`

**Success Response (200):**
```json
{
  "patient": {
    "id": 101,
    "firstName": "Amit",
    "lastName": "Sharma",
    "dateOfBirth": "1990-05-15",
    "gender": "MALE",
    "bloodGroup": "B_POSITIVE",
    "allergies": "Penicillin"
  },
  "visits": [
    {
      "appointmentId": 501,
      "date": "2026-03-01",
      "doctorName": "Dr. Rajesh Kumar",
      "department": "General Medicine",
      "diagnosis": "Viral Fever",
      "severity": "MILD",
      "clinicalNotes": "Patient presented with high fever for 3 days",
      "followUpDate": "2026-03-08",
      "prescriptions": [
        {
          "medicineName": "Paracetamol",
          "medicineStrength": "500mg",
          "dosage": "500mg",
          "frequency": "Thrice daily",
          "durationDays": 5,
          "instructions": "Take after meals"
        }
      ]
    }
  ]
}
```

---

### 7.2 Appointment APIs

#### POST /api/appointments -- Book Scheduled Appointment

**Access:** `ROLE_RECEPTIONIST`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| patientId | number | Yes | Patient ID |
| doctorId | number | Yes | Doctor ID |
| appointmentDate | string | Yes | Format: YYYY-MM-DD (future date, max 30 days) |
| appointmentTime | string | Yes | Format: HH:mm (must be available slot) |
| notes | string | No | Additional notes |

```json
{
  "patientId": 101,
  "doctorId": 5,
  "appointmentDate": "2026-03-15",
  "appointmentTime": "10:30",
  "notes": "Follow-up for diabetes"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Appointment booked successfully",
  "data": {
    "id": 502,
    "patientId": 101,
    "patientName": "Amit Sharma",
    "doctorId": 5,
    "doctorName": "Dr. Rajesh Kumar",
    "department": "General Medicine",
    "appointmentDate": "2026-03-15",
    "appointmentTime": "10:30",
    "type": "SCHEDULED",
    "tokenNumber": null,
    "status": "BOOKED",
    "notes": "Follow-up for diabetes",
    "createdAt": "2026-03-11T10:30:00"
  },
  "timestamp": "2026-03-11T10:30:00"
}
```

**Error (409 -- slot taken):**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Time slot 10:30 is already booked for Dr. Rajesh Kumar on 2026-03-15",
  "timestamp": "2026-03-11T10:30:00"
}
```

---

#### POST /api/appointments/walk-in -- Walk-in Token

**Access:** `ROLE_RECEPTIONIST`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| patientId | number | Yes | Patient ID |
| doctorId | number | Yes | Doctor ID |
| notes | string | No | Reason for visit |

```json
{
  "patientId": 101,
  "doctorId": 5,
  "notes": "Fever and headache"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Walk-in token generated",
  "data": {
    "id": 503,
    "patientId": 101,
    "patientName": "Amit Sharma",
    "doctorId": 5,
    "doctorName": "Dr. Rajesh Kumar",
    "department": "General Medicine",
    "appointmentDate": "2026-03-11",
    "appointmentTime": null,
    "type": "WALK_IN",
    "tokenNumber": 7,
    "status": "BOOKED",
    "notes": "Fever and headache",
    "createdAt": "2026-03-11T10:35:00"
  },
  "timestamp": "2026-03-11T10:35:00"
}
```

**UI Tip:** Display the `tokenNumber` prominently -- this is the patient's queue number.

---

#### GET /api/appointments -- List Appointments (Filtered)

**Access:** `ROLE_RECEPTIONIST`

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | number | 0 | Page number |
| size | number | 20 | Items per page |
| date | string | today | Filter by date (YYYY-MM-DD) |
| doctorId | number | | Filter by doctor |
| status | string | | Filter by status |
| type | string | | SCHEDULED or WALK_IN |
| patientId | number | | Filter by patient |

**Example:** `GET /api/appointments?date=2026-03-11&doctorId=5&status=BOOKED`

**Success Response (200):**
```json
{
  "content": [
    {
      "id": 502,
      "patientId": 101,
      "patientName": "Amit Sharma",
      "doctorId": 5,
      "doctorName": "Dr. Rajesh Kumar",
      "department": "General Medicine",
      "appointmentDate": "2026-03-11",
      "appointmentTime": "10:30",
      "type": "SCHEDULED",
      "tokenNumber": null,
      "status": "BOOKED",
      "notes": "Follow-up"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

#### GET /api/appointments/doctor/{doctorId}/slots?date=2026-03-15

**Access:** `ROLE_RECEPTIONIST`

**Description:** Get available time slots for a doctor on a specific date. Use this to populate a slot picker when booking appointments.

**Path Params:** `doctorId` (number)
**Query Params:** `date` (string, YYYY-MM-DD)

**Success Response (200):**
```json
{
  "doctorId": 5,
  "doctorName": "Dr. Rajesh Kumar",
  "date": "2026-03-15",
  "slotDurationMinutes": 30,
  "availableSlots": [
    "09:00", "09:30", "10:00", "11:00", "11:30",
    "14:00", "14:30", "15:00", "15:30", "16:00", "16:30"
  ],
  "bookedSlots": [
    "10:30", "12:00", "13:00", "13:30"
  ]
}
```

**UI Tip:** Show `availableSlots` as clickable buttons (green). Show `bookedSlots` as disabled (gray).

---

#### GET /api/appointments/doctor/{doctorId}/today -- Doctor's Today Queue

**Access:** `ROLE_DOCTOR`

**Description:** Returns today's appointments for the logged-in doctor, sorted by token number (walk-in) and time (scheduled).

**Success Response (200):**
```json
[
  {
    "id": 500,
    "patientId": 99,
    "patientName": "Ravi Patel",
    "tokenNumber": 1,
    "type": "WALK_IN",
    "appointmentTime": null,
    "status": "CHECKED_IN",
    "notes": "Cough and cold"
  },
  {
    "id": 501,
    "patientId": 100,
    "patientName": "Sunita Devi",
    "tokenNumber": 2,
    "type": "WALK_IN",
    "appointmentTime": null,
    "status": "BOOKED",
    "notes": "Stomach pain"
  },
  {
    "id": 502,
    "patientId": 101,
    "patientName": "Amit Sharma",
    "tokenNumber": null,
    "type": "SCHEDULED",
    "appointmentTime": "10:30",
    "status": "BOOKED",
    "notes": "Follow-up for diabetes"
  }
]
```

**UI Tip:** Show walk-in patients with token numbers in a queue list. Show scheduled patients in a separate time-based list. Highlight the current patient (IN_PROGRESS status).

---

#### PUT /api/appointments/{id}/status -- Update Appointment Status

**Access:** `ROLE_RECEPTIONIST`, `ROLE_DOCTOR`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| status | string | Yes | New status value |

```json
{
  "status": "CHECKED_IN"
}
```

**Valid Status Transitions:**

```
BOOKED       → CHECKED_IN    (Receptionist: patient arrived)
CHECKED_IN   → IN_PROGRESS   (Doctor: starting consultation)
IN_PROGRESS  → COMPLETED     (Doctor: consultation done)
BOOKED       → CANCELLED     (Receptionist: patient cancelled)
CHECKED_IN   → CANCELLED     (Receptionist: patient left)
```

**Error (400 -- invalid transition):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot transition from COMPLETED to IN_PROGRESS",
  "timestamp": "2026-03-11T10:30:00"
}
```

---

### 7.3 Billing APIs

#### POST /api/billings -- Generate Bill

**Access:** `ROLE_RECEPTIONIST`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| appointmentId | number | No | Link to appointment |
| patientId | number | Yes | Patient ID |
| discount | number | No | Discount amount (default: 0) |
| tax | number | No | Tax amount (default: 0) |
| paymentMethod | string | No | Enum: CASH, CARD, UPI, INSURANCE |
| items | array | Yes | Billing line items |

**Billing Item:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| description | string | Yes | Item description |
| amount | number | Yes | Unit price |
| quantity | number | Yes | Quantity |
| itemType | string | Yes | Enum: CONSULTATION, LAB, MEDICINE, PROCEDURE |

```json
{
  "appointmentId": 502,
  "patientId": 101,
  "discount": 100.00,
  "tax": 50.00,
  "paymentMethod": "UPI",
  "items": [
    {
      "description": "Consultation Fee - Dr. Rajesh Kumar",
      "amount": 500.00,
      "quantity": 1,
      "itemType": "CONSULTATION"
    },
    {
      "description": "Metformin 500mg x 60 tablets",
      "amount": 120.00,
      "quantity": 1,
      "itemType": "MEDICINE"
    },
    {
      "description": "HbA1c Blood Test",
      "amount": 350.00,
      "quantity": 1,
      "itemType": "LAB"
    }
  ]
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Bill generated successfully",
  "data": {
    "id": 601,
    "invoiceNumber": "INV-20260311-001",
    "appointmentId": 502,
    "patientId": 101,
    "patientName": "Amit Sharma",
    "totalAmount": 970.00,
    "discount": 100.00,
    "tax": 50.00,
    "netAmount": 920.00,
    "paymentStatus": "PENDING",
    "paymentMethod": "UPI",
    "items": [
      {
        "id": 1,
        "description": "Consultation Fee - Dr. Rajesh Kumar",
        "amount": 500.00,
        "quantity": 1,
        "itemType": "CONSULTATION"
      },
      {
        "id": 2,
        "description": "Metformin 500mg x 60 tablets",
        "amount": 120.00,
        "quantity": 1,
        "itemType": "MEDICINE"
      },
      {
        "id": 3,
        "description": "HbA1c Blood Test",
        "amount": 350.00,
        "quantity": 1,
        "itemType": "LAB"
      }
    ],
    "createdAt": "2026-03-11T12:00:00"
  },
  "timestamp": "2026-03-11T12:00:00"
}
```

**Calculation:** `totalAmount = SUM(item.amount * item.quantity)`, `netAmount = totalAmount - discount + tax`

---

#### GET /api/billings/{id} -- Get Bill Details

**Access:** `ROLE_RECEPTIONIST`, `ROLE_ADMIN`

**Response:** Same structure as POST response `data` field.

---

#### PUT /api/billings/{id}/payment -- Update Payment Status

**Access:** `ROLE_RECEPTIONIST`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| paymentStatus | string | Yes | PAID, PARTIAL, CANCELLED |
| paymentMethod | string | No | CASH, CARD, UPI, INSURANCE |

```json
{
  "paymentStatus": "PAID",
  "paymentMethod": "UPI"
}
```

---

#### GET /api/billings/patient/{patientId} -- Patient Billing History

**Access:** `ROLE_RECEPTIONIST`

**Query Params:** `page`, `size`

**Response:** Paginated list of billing summaries.

```json
{
  "content": [
    {
      "id": 601,
      "invoiceNumber": "INV-20260311-001",
      "appointmentDate": "2026-03-11",
      "doctorName": "Dr. Rajesh Kumar",
      "netAmount": 920.00,
      "paymentStatus": "PAID",
      "paymentMethod": "UPI",
      "createdAt": "2026-03-11T12:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

## 8. API Reference: Doctor Module

### 8.1 Diagnosis APIs

#### POST /api/diagnoses -- Create Diagnosis

**Access:** `ROLE_DOCTOR`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| appointmentId | number | Yes | Must be IN_PROGRESS status |
| symptoms | string | Yes | Patient symptoms |
| diagnosis | string | Yes | Doctor's diagnosis |
| clinicalNotes | string | No | Additional notes |
| severity | string | No | Enum: MILD, MODERATE, SEVERE, CRITICAL |
| followUpDate | string | No | Format: YYYY-MM-DD |

```json
{
  "appointmentId": 502,
  "symptoms": "High blood sugar levels, frequent urination, fatigue",
  "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
  "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes.",
  "severity": "MODERATE",
  "followUpDate": "2026-04-15"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Diagnosis created successfully",
  "data": {
    "id": 301,
    "appointmentId": 502,
    "doctorId": 5,
    "doctorName": "Dr. Rajesh Kumar",
    "patientId": 101,
    "patientName": "Amit Sharma",
    "symptoms": "High blood sugar levels, frequent urination, fatigue",
    "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
    "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes.",
    "severity": "MODERATE",
    "followUpDate": "2026-04-15",
    "prescriptions": [],
    "createdAt": "2026-03-11T11:00:00"
  },
  "timestamp": "2026-03-11T11:00:00"
}
```

---

#### GET /api/diagnoses/{id} -- Get Diagnosis

**Access:** `ROLE_DOCTOR`

**Response:** Same as POST response `data` field, with prescriptions included.

---

#### PUT /api/diagnoses/{id} -- Update Diagnosis

**Access:** `ROLE_DOCTOR`

**Request Body:** Same as POST (only provided fields are updated).

---

#### GET /api/diagnoses/appointment/{appointmentId} -- Get Diagnosis by Appointment

**Access:** `ROLE_DOCTOR`

**Response:** Same structure as GET by ID.

---

### 8.2 Prescription APIs

#### POST /api/prescriptions -- Add Prescriptions (Batch)

**Access:** `ROLE_DOCTOR`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| diagnosisId | number | Yes | Parent diagnosis ID |
| prescriptions | array | Yes | List of prescription items |

**Prescription Item:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| medicineId | number | Yes | Medicine ID (from search) |
| dosage | string | Yes | e.g., "500mg" |
| frequency | string | Yes | e.g., "Twice daily" |
| durationDays | number | Yes | Number of days |
| instructions | string | No | e.g., "Take after meals" |

```json
{
  "diagnosisId": 301,
  "prescriptions": [
    {
      "medicineId": 10,
      "dosage": "500mg",
      "frequency": "Twice daily",
      "durationDays": 30,
      "instructions": "Take after meals"
    },
    {
      "medicineId": 25,
      "dosage": "5mg",
      "frequency": "Once daily (morning)",
      "durationDays": 30,
      "instructions": "Take before breakfast"
    }
  ]
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Prescriptions added successfully",
  "data": [
    {
      "id": 401,
      "medicineId": 10,
      "medicineName": "Metformin",
      "medicineStrength": "500mg",
      "medicineForm": "TABLET",
      "dosage": "500mg",
      "frequency": "Twice daily",
      "durationDays": 30,
      "instructions": "Take after meals"
    },
    {
      "id": 402,
      "medicineId": 25,
      "medicineName": "Glimepiride",
      "medicineStrength": "5mg",
      "medicineForm": "TABLET",
      "dosage": "5mg",
      "frequency": "Once daily (morning)",
      "durationDays": 30,
      "instructions": "Take before breakfast"
    }
  ],
  "timestamp": "2026-03-11T11:05:00"
}
```

---

#### GET /api/prescriptions/diagnosis/{diagnosisId} -- Get Prescriptions

**Access:** `ROLE_DOCTOR`

**Response:** Array of prescription objects (same structure as POST response data).

---

#### PUT /api/prescriptions/{id} -- Update Prescription

**Access:** `ROLE_DOCTOR`

**Request Body:** Single prescription item fields (medicineId, dosage, frequency, durationDays, instructions).

---

#### DELETE /api/prescriptions/{id} -- Remove Prescription

**Access:** `ROLE_DOCTOR`

**Response:** 204 No Content

---

### 8.3 Medicine Search API

#### GET /api/medicines/search?query=parac

**Access:** `ROLE_DOCTOR`, `ROLE_ADMIN`

**Description:** Autocomplete search for medicines. Returns top 10 matches. Use this to populate a dropdown/autocomplete when the doctor is writing prescriptions.

**Query Params:** `query` (string, min 2 chars)

**Success Response (200):**
```json
[
  {
    "id": 10,
    "name": "Paracetamol",
    "strength": "500mg",
    "form": "TABLET"
  },
  {
    "id": 11,
    "name": "Paracetamol",
    "strength": "650mg",
    "form": "TABLET"
  },
  {
    "id": 12,
    "name": "Paracetamol Syrup",
    "strength": "250mg/5ml",
    "form": "SYRUP"
  }
]
```

**UI Tip:** Implement as a debounced autocomplete input (300ms delay). Show name + strength + form in dropdown options.

---

## 9. API Reference: Admin Module

### 9.1 User Management

#### POST /api/admin/users -- Create User

**Access:** `ROLE_ADMIN`

**Request Body (Create Doctor):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | Yes | Unique login email |
| password | string | Yes | Temporary password (min 8 chars) |
| fullName | string | Yes | Display name |
| phone | string | No | Contact number |
| role | string | Yes | `ROLE_DOCTOR` or `ROLE_RECEPTIONIST` |
| doctorDetails | object | If DOCTOR | Doctor-specific fields |
| receptionistDetails | object | If RECEPTIONIST | Receptionist-specific fields |

**Doctor Details:**

| Field | Type | Required |
|-------|------|----------|
| departmentId | number | Yes |
| specialization | string | Yes |
| licenseNumber | string | Yes |
| qualification | string | No |
| experienceYears | number | No |
| consultationFee | number | Yes |

**Receptionist Details:**

| Field | Type | Required |
|-------|------|----------|
| employeeId | string | Yes |
| shift | string | No (MORNING, AFTERNOON, NIGHT) |

```json
{
  "email": "newdoctor@hospital.com",
  "password": "tempPassword123",
  "fullName": "Dr. Priya Mehta",
  "phone": "9876543222",
  "role": "ROLE_DOCTOR",
  "doctorDetails": {
    "departmentId": 2,
    "specialization": "Cardiology",
    "licenseNumber": "MCI-12345",
    "qualification": "MBBS, MD (Cardiology)",
    "experienceYears": 10,
    "consultationFee": 800.00
  }
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 15,
    "email": "newdoctor@hospital.com",
    "fullName": "Dr. Priya Mehta",
    "phone": "9876543222",
    "role": "ROLE_DOCTOR",
    "isActive": true,
    "createdAt": "2026-03-11T10:00:00"
  },
  "timestamp": "2026-03-11T10:00:00"
}
```

---

#### GET /api/admin/users -- List Users

**Access:** `ROLE_ADMIN`

**Query Params:** `page`, `size`, `role` (optional filter), `search` (by name/email)

**Example:** `GET /api/admin/users?role=ROLE_DOCTOR&page=0&size=20`

**Success Response (200):**
```json
{
  "content": [
    {
      "id": 5,
      "email": "doctor@hospital.com",
      "fullName": "Dr. Rajesh Kumar",
      "phone": "9876543210",
      "role": "ROLE_DOCTOR",
      "isActive": true,
      "department": "General Medicine",
      "specialization": "General Medicine",
      "createdAt": "2026-01-15T09:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 25,
  "totalPages": 2,
  "last": false
}
```

---

#### PUT /api/admin/users/{id} -- Update User

**Access:** `ROLE_ADMIN`

**Request Body:** Same as POST (only provided fields are updated). Cannot change role.

---

#### DELETE /api/admin/users/{id} -- Deactivate User

**Access:** `ROLE_ADMIN`

**Description:** Soft-deletes the user (sets `isActive = false`). Does NOT permanently delete.

**Response:** 204 No Content

---

### 9.2 Department Management

#### POST /api/admin/departments -- Create Department

**Access:** `ROLE_ADMIN`

```json
{
  "name": "Neurology",
  "description": "Brain and nervous system disorders"
}
```

#### GET /api/admin/departments -- List Departments

**Access:** All authenticated users

```json
[
  { "id": 1, "name": "General Medicine", "description": "General health consultations", "isActive": true },
  { "id": 2, "name": "Cardiology", "description": "Heart and cardiovascular system", "isActive": true },
  { "id": 3, "name": "Orthopedics", "description": "Bones, joints, and muscles", "isActive": true }
]
```

**UI Tip:** Use this to populate department dropdowns when creating doctors or filtering.

#### PUT /api/admin/departments/{id} -- Update Department

**Access:** `ROLE_ADMIN`

---

### 9.3 Medicine Management

#### POST /api/medicines -- Add Medicine

**Access:** `ROLE_ADMIN`

```json
{
  "name": "Amoxicillin",
  "genericName": "Amoxicillin Trihydrate",
  "brand": "Mox",
  "strength": "500mg",
  "form": "CAPSULE",
  "category": "Antibiotic"
}
```

#### PUT /api/medicines/{id} -- Update Medicine

**Access:** `ROLE_ADMIN`

---

### 9.4 Inventory Management

#### GET /api/inventory -- View Inventory

**Access:** `ROLE_ADMIN`

**Query Params:** `page`, `size`, `search` (medicine name), `lowStock` (boolean), `expiringSoon` (boolean)

**Example:** `GET /api/inventory?lowStock=true&expiringSoon=true`

```json
{
  "content": [
    {
      "id": 1,
      "medicineId": 10,
      "medicineName": "Paracetamol 500mg",
      "quantity": 5,
      "unitPrice": 2.50,
      "expiryDate": "2026-04-01",
      "batchNumber": "BATCH-2025-001",
      "isLowStock": true,
      "isExpiringSoon": true,
      "lastUpdated": "2026-03-01T10:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 3,
  "totalPages": 1,
  "last": true
}
```

**UI Tip:** Highlight rows where `isLowStock` or `isExpiringSoon` is true with warning colors.

#### POST /api/inventory -- Add Stock

**Access:** `ROLE_ADMIN`

```json
{
  "medicineId": 10,
  "quantity": 500,
  "unitPrice": 2.50,
  "expiryDate": "2027-03-01",
  "batchNumber": "BATCH-2026-015"
}
```

#### PUT /api/inventory/{id} -- Update Stock

**Access:** `ROLE_ADMIN`

---

## 10. API Reference: Reports

### GET /api/reports/dashboard -- Admin Dashboard

**Access:** `ROLE_ADMIN`

**Success Response (200):**
```json
{
  "totalPatients": 1250,
  "totalDoctors": 25,
  "totalReceptionists": 8,
  "todayAppointments": 47,
  "todayCompletedAppointments": 19,
  "todayRevenue": 35600.00,
  "pendingBills": 12,
  "monthlyRevenue": 856000.00,
  "appointmentsByStatus": {
    "BOOKED": 15,
    "CHECKED_IN": 8,
    "IN_PROGRESS": 5,
    "COMPLETED": 19,
    "CANCELLED": 0
  },
  "recentAppointments": [
    {
      "id": 503,
      "patientName": "Amit Sharma",
      "doctorName": "Dr. Rajesh Kumar",
      "status": "IN_PROGRESS",
      "time": "10:30"
    }
  ]
}
```

**UI Tip:** Use this for stat cards (total patients, today's revenue, etc.) and a pie chart for appointments by status.

---

### GET /api/reports/revenue?from=2026-03-01&to=2026-03-11

**Access:** `ROLE_ADMIN`

**Query Params:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| from | string | Yes | Start date (YYYY-MM-DD) |
| to | string | Yes | End date (YYYY-MM-DD) |

**Success Response (200):**
```json
{
  "from": "2026-03-01",
  "to": "2026-03-11",
  "totalRevenue": 425000.00,
  "totalBills": 312,
  "averageBillAmount": 1362.18,
  "dailyBreakdown": [
    { "date": "2026-03-01", "revenue": 38500.00, "billCount": 28 },
    { "date": "2026-03-02", "revenue": 42000.00, "billCount": 31 },
    { "date": "2026-03-03", "revenue": 35000.00, "billCount": 25 }
  ],
  "revenueByType": {
    "CONSULTATION": 156000.00,
    "MEDICINE": 98000.00,
    "LAB": 125000.00,
    "PROCEDURE": 46000.00
  }
}
```

**UI Tip:** Use `dailyBreakdown` for a line/bar chart. Use `revenueByType` for a pie/donut chart.

---

### GET /api/reports/patients/count?from=2026-03-01&to=2026-03-11

**Access:** `ROLE_ADMIN`

**Success Response (200):**
```json
{
  "from": "2026-03-01",
  "to": "2026-03-11",
  "totalNewPatients": 85,
  "dailyBreakdown": [
    { "date": "2026-03-01", "count": 12 },
    { "date": "2026-03-02", "count": 8 },
    { "date": "2026-03-03", "count": 15 }
  ]
}
```

---

### GET /api/reports/doctors/performance?from=2026-03-01&to=2026-03-11

**Access:** `ROLE_ADMIN`

**Success Response (200):**
```json
[
  {
    "doctorId": 5,
    "doctorName": "Dr. Rajesh Kumar",
    "department": "General Medicine",
    "totalAppointments": 85,
    "completedAppointments": 78,
    "cancelledAppointments": 3,
    "inProgressAppointments": 4,
    "totalRevenue": 62400.00,
    "averageConsultationTime": null
  },
  {
    "doctorId": 8,
    "doctorName": "Dr. Priya Mehta",
    "department": "Cardiology",
    "totalAppointments": 62,
    "completedAppointments": 58,
    "cancelledAppointments": 2,
    "inProgressAppointments": 2,
    "totalRevenue": 49600.00,
    "averageConsultationTime": null
  }
]
```

**UI Tip:** Display as a sortable table. Add bar charts for visual comparison.

---

## 11. Enum Values Reference

Use these exact string values in request bodies and expect them in responses.

### Gender
| Value | Display |
|-------|---------|
| `MALE` | Male |
| `FEMALE` | Female |
| `OTHER` | Other |

### Blood Group
| Value | Display |
|-------|---------|
| `A_POSITIVE` | A+ |
| `A_NEGATIVE` | A- |
| `B_POSITIVE` | B+ |
| `B_NEGATIVE` | B- |
| `O_POSITIVE` | O+ |
| `O_NEGATIVE` | O- |
| `AB_POSITIVE` | AB+ |
| `AB_NEGATIVE` | AB- |

### Appointment Type
| Value | Display |
|-------|---------|
| `SCHEDULED` | Scheduled |
| `WALK_IN` | Walk-in |

### Appointment Status
| Value | Display | Color Suggestion |
|-------|---------|-----------------|
| `BOOKED` | Booked | Blue |
| `CHECKED_IN` | Checked In | Orange |
| `IN_PROGRESS` | In Progress | Yellow |
| `COMPLETED` | Completed | Green |
| `CANCELLED` | Cancelled | Red |

### Severity
| Value | Display | Color Suggestion |
|-------|---------|-----------------|
| `MILD` | Mild | Green |
| `MODERATE` | Moderate | Yellow |
| `SEVERE` | Severe | Orange |
| `CRITICAL` | Critical | Red |

### Payment Status
| Value | Display | Color Suggestion |
|-------|---------|-----------------|
| `PENDING` | Pending | Orange |
| `PAID` | Paid | Green |
| `PARTIAL` | Partial | Yellow |
| `CANCELLED` | Cancelled | Red |

### Payment Method
| Value | Display |
|-------|---------|
| `CASH` | Cash |
| `CARD` | Card |
| `UPI` | UPI |
| `INSURANCE` | Insurance |

### Billing Item Type
| Value | Display |
|-------|---------|
| `CONSULTATION` | Consultation |
| `LAB` | Lab Test |
| `MEDICINE` | Medicine |
| `PROCEDURE` | Procedure |

### Shift (Receptionist)
| Value | Display |
|-------|---------|
| `MORNING` | Morning |
| `AFTERNOON` | Afternoon |
| `NIGHT` | Night |

### User Role
| Value | Display |
|-------|---------|
| `ROLE_ADMIN` | Admin |
| `ROLE_DOCTOR` | Doctor |
| `ROLE_RECEPTIONIST` | Receptionist |

---

## 12. Pagination Guide

### Request Parameters

All list endpoints support pagination:

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | number | 0 | Page number (0-based) |
| `size` | number | 20 | Items per page (max 100) |
| `sortBy` | string | varies | Field to sort by |
| `sortDir` | string | desc | `asc` or `desc` |

### Response Structure

```typescript
interface PagedResponse<T> {
  content: T[];           // items on this page
  pageNumber: number;     // current page (0-based)
  pageSize: number;       // items per page
  totalElements: number;  // total items across all pages
  totalPages: number;     // total pages
  last: boolean;          // true if last page
}
```

### Frontend Pagination Example

```typescript
// React example with pagination
const [page, setPage] = useState(0);
const [data, setData] = useState<PagedResponse<Patient>>();

useEffect(() => {
  api.get(`/patients?page=${page}&size=20&search=${searchTerm}`)
    .then(res => setData(res.data));
}, [page, searchTerm]);

// Render pagination controls
<Pagination
  currentPage={data.pageNumber}
  totalPages={data.totalPages}
  onPageChange={setPage}
/>
```

---

## 13. Suggested Screen Layouts

### 13.1 Login Screen
- Email input field
- Password input field
- Login button
- Error message area
- After login: redirect based on role

### 13.2 Receptionist Dashboard
```
┌─────────────────────────────────────────────────────┐
│  CuraMatrix HSM          [Receptionist Name] [Logout]│
├──────────┬──────────────────────────────────────────┤
│          │                                          │
│ Sidebar  │  Main Content Area                       │
│          │                                          │
│ Patients │  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│ Appoint- │  │Today's   │ │Pending   │ │Walk-in   │ │
│  ments   │  │Appoint.  │ │Bills     │ │Queue     │ │
│ Billing  │  │  47      │ │  12      │ │  15      │ │
│          │  └──────────┘ └──────────┘ └──────────┘ │
│          │                                          │
│          │  [Recent Appointments Table]              │
│          │                                          │
└──────────┴──────────────────────────────────────────┘
```

### 13.3 Doctor Queue Screen
```
┌─────────────────────────────────────────────────────┐
│  CuraMatrix HSM              [Dr. Name]     [Logout]│
├──────────┬──────────────────────────────────────────┤
│          │                                          │
│ Sidebar  │  Today's Queue - March 11, 2026          │
│          │                                          │
│ Queue    │  Walk-in Patients:                       │
│ History  │  ┌─────────────────────────────────────┐ │
│          │  │ #1 Ravi Patel    [CHECKED_IN] [Start]│ │
│          │  │ #2 Sunita Devi   [BOOKED]            │ │
│          │  │ #3 Mohan Singh   [BOOKED]            │ │
│          │  └─────────────────────────────────────┘ │
│          │                                          │
│          │  Scheduled:                              │
│          │  ┌─────────────────────────────────────┐ │
│          │  │ 10:30 Amit Sharma [BOOKED]           │ │
│          │  │ 11:00 Priya Patel [BOOKED]           │ │
│          │  └─────────────────────────────────────┘ │
└──────────┴──────────────────────────────────────────┘
```

### 13.4 Diagnosis + Prescription Screen
```
┌─────────────────────────────────────────────────────┐
│  Patient: Amit Sharma (M, 35y) | Blood: B+ | Allergy: Penicillin │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Symptoms:  [________________________]              │
│  Diagnosis: [________________________]              │
│  Notes:     [________________________]              │
│  Severity:  [MILD ▼]                                │
│  Follow-up: [2026-04-15]                            │
│                                                     │
│  Prescriptions:                                     │
│  ┌────────────────────────────────────────────────┐ │
│  │ Medicine [Autocomplete] Dosage  Freq  Days     │ │
│  │ Metformin 500mg         500mg   2x/d  30  [x] │ │
│  │ Glimepiride 5mg         5mg     1x/d  30  [x] │ │
│  │ [+ Add Medicine]                               │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  [Save Diagnosis]  [Complete Appointment]           │
└─────────────────────────────────────────────────────┘
```

### 13.5 Admin Dashboard
```
┌─────────────────────────────────────────────────────┐
│  CuraMatrix HSM                  [Admin]    [Logout]│
├──────────┬──────────────────────────────────────────┤
│          │                                          │
│ Sidebar  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │
│          │  │Patie-│ │Doct- │ │Today │ │Reve- │   │
│ Dashboard│  │nts   │ │ors   │ │Appt  │ │nue   │   │
│ Users    │  │1250  │ │ 25   │ │ 47   │ │35.6K │   │
│ Depart-  │  └──────┘ └──────┘ └──────┘ └──────┘   │
│  ments   │                                          │
│ Medicines│  ┌─────────────────┐ ┌────────────────┐ │
│ Inventory│  │ Revenue Chart   │ │ Appointments   │ │
│ Reports  │  │ (Line/Bar)      │ │ (Pie Chart)    │ │
│          │  └─────────────────┘ └────────────────┘ │
│          │                                          │
│          │  [Doctor Performance Table]               │
└──────────┴──────────────────────────────────────────┘
```

---

## 14. WebSocket / Real-time (Future)

Currently not implemented. Future enhancements may include:
- Real-time queue updates for doctors (new patient checked in)
- Live appointment status changes for receptionists
- Notification system

For now, use **polling** (refresh every 30 seconds) for the doctor queue screen:

```typescript
useEffect(() => {
  const interval = setInterval(() => {
    fetchTodayQueue();
  }, 30000); // 30 seconds
  return () => clearInterval(interval);
}, []);
```

---

## 15. CORS Configuration

The backend is configured to allow requests from the frontend development server:

**Allowed Origins:** `http://localhost:3000`, `http://localhost:5173` (Vite)
**Allowed Methods:** GET, POST, PUT, DELETE, OPTIONS
**Allowed Headers:** Authorization, Content-Type
**Exposed Headers:** Authorization

If you run the frontend on a different port, inform the backend team to update `CorsConfig.java`.

---

## 16. Quick Start Checklist

### For Frontend Developer

1. **Get the backend running:**
   - Ensure MySQL is running on `localhost:3306`
   - Database `hospitalsystems` exists
   - Run the Spring Boot app: `./gradlew bootRun`
   - Verify: `http://localhost:8080/swagger-ui.html`

2. **Test login:**
   - Default admin: `admin@curamatrix.com` / `admin123`
   - Use Swagger UI or Postman to test `POST /api/auth/login`

3. **Set up API client:**
   - Create an Axios/Fetch wrapper with base URL `http://localhost:8080/api`
   - Add JWT interceptor for Authorization header
   - Add 401 interceptor for redirect to login

4. **Implement screens in order:**
   - Login screen
   - Role-based routing
   - Receptionist: Patient registration → Appointment booking → Billing
   - Doctor: Queue → Diagnosis → Prescription
   - Admin: Dashboard → User management → Reports

5. **Use Swagger UI** (`http://localhost:8080/swagger-ui.html`) to:
   - Explore all endpoints interactively
   - See exact request/response schemas
   - Test APIs directly from the browser

---

## TypeScript Type Definitions

For convenience, here are TypeScript interfaces matching the API:

```typescript
// ============ AUTH ============
interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  token: string;
  tokenType: string;
  userId: number;
  fullName: string;
  role: 'ROLE_ADMIN' | 'ROLE_DOCTOR' | 'ROLE_RECEPTIONIST';
  expiresIn: number;
}

// ============ PATIENT ============
interface PatientRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;       // YYYY-MM-DD
  gender: 'MALE' | 'FEMALE' | 'OTHER';
  phone: string;
  email?: string;
  address?: string;
  bloodGroup?: BloodGroup;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  allergies?: string;
  medicalHistory?: string;
}

interface PatientResponse {
  id: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  phone: string;
  email?: string;
  address?: string;
  bloodGroup?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  allergies?: string;
  medicalHistory?: string;
  registeredAt: string;
}

// ============ APPOINTMENT ============
interface AppointmentRequest {
  patientId: number;
  doctorId: number;
  appointmentDate: string;   // YYYY-MM-DD
  appointmentTime: string;   // HH:mm
  notes?: string;
}

interface WalkInRequest {
  patientId: number;
  doctorId: number;
  notes?: string;
}

interface AppointmentResponse {
  id: number;
  patientId: number;
  patientName: string;
  doctorId: number;
  doctorName: string;
  department: string;
  appointmentDate: string;
  appointmentTime?: string;
  type: 'SCHEDULED' | 'WALK_IN';
  tokenNumber?: number;
  status: AppointmentStatus;
  notes?: string;
  createdAt: string;
}

interface SlotResponse {
  doctorId: number;
  doctorName: string;
  date: string;
  slotDurationMinutes: number;
  availableSlots: string[];
  bookedSlots: string[];
}

// ============ DIAGNOSIS ============
interface DiagnosisRequest {
  appointmentId: number;
  symptoms: string;
  diagnosis: string;
  clinicalNotes?: string;
  severity?: 'MILD' | 'MODERATE' | 'SEVERE' | 'CRITICAL';
  followUpDate?: string;
}

interface DiagnosisResponse {
  id: number;
  appointmentId: number;
  doctorId: number;
  doctorName: string;
  patientId: number;
  patientName: string;
  symptoms: string;
  diagnosis: string;
  clinicalNotes?: string;
  severity?: string;
  followUpDate?: string;
  prescriptions: PrescriptionResponse[];
  createdAt: string;
}

// ============ PRESCRIPTION ============
interface PrescriptionRequest {
  diagnosisId: number;
  prescriptions: PrescriptionItemRequest[];
}

interface PrescriptionItemRequest {
  medicineId: number;
  dosage: string;
  frequency: string;
  durationDays: number;
  instructions?: string;
}

interface PrescriptionResponse {
  id: number;
  medicineId: number;
  medicineName: string;
  medicineStrength: string;
  medicineForm: string;
  dosage: string;
  frequency: string;
  durationDays: number;
  instructions?: string;
}

// ============ BILLING ============
interface BillingRequest {
  appointmentId?: number;
  patientId: number;
  discount?: number;
  tax?: number;
  paymentMethod?: PaymentMethod;
  items: BillingItemRequest[];
}

interface BillingItemRequest {
  description: string;
  amount: number;
  quantity: number;
  itemType: 'CONSULTATION' | 'LAB' | 'MEDICINE' | 'PROCEDURE';
}

interface BillingResponse {
  id: number;
  invoiceNumber: string;
  appointmentId?: number;
  patientId: number;
  patientName: string;
  totalAmount: number;
  discount: number;
  tax: number;
  netAmount: number;
  paymentStatus: PaymentStatus;
  paymentMethod?: string;
  items: BillingItemResponse[];
  createdAt: string;
}

// ============ ADMIN ============
interface CreateUserRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_DOCTOR' | 'ROLE_RECEPTIONIST';
  doctorDetails?: DoctorDetails;
  receptionistDetails?: ReceptionistDetails;
}

interface DoctorDetails {
  departmentId: number;
  specialization: string;
  licenseNumber: string;
  qualification?: string;
  experienceYears?: number;
  consultationFee: number;
}

interface ReceptionistDetails {
  employeeId: string;
  shift?: 'MORNING' | 'AFTERNOON' | 'NIGHT';
}

interface DepartmentResponse {
  id: number;
  name: string;
  description?: string;
  isActive: boolean;
}

// ============ REPORTS ============
interface DashboardResponse {
  totalPatients: number;
  totalDoctors: number;
  totalReceptionists: number;
  todayAppointments: number;
  todayCompletedAppointments: number;
  todayRevenue: number;
  pendingBills: number;
  monthlyRevenue: number;
  appointmentsByStatus: Record<AppointmentStatus, number>;
  recentAppointments: AppointmentSummary[];
}

interface RevenueReportResponse {
  from: string;
  to: string;
  totalRevenue: number;
  totalBills: number;
  averageBillAmount: number;
  dailyBreakdown: { date: string; revenue: number; billCount: number }[];
  revenueByType: Record<string, number>;
}

// ============ GENERIC ============
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  validationErrors?: Record<string, string>;
}

// ============ ENUMS ============
type AppointmentStatus = 'BOOKED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
type PaymentStatus = 'PENDING' | 'PAID' | 'PARTIAL' | 'CANCELLED';
type PaymentMethod = 'CASH' | 'CARD' | 'UPI' | 'INSURANCE';
type BloodGroup = 'A_POSITIVE' | 'A_NEGATIVE' | 'B_POSITIVE' | 'B_NEGATIVE' |
                  'O_POSITIVE' | 'O_NEGATIVE' | 'AB_POSITIVE' | 'AB_NEGATIVE';
```

---

**End of Frontend Integration Guide**
**CuraMatrix HSM v1.0.0**
