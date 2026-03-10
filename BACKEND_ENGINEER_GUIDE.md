BFL0226000009788# CuraMatrix Hospital Management System (HSM)
# Backend Engineer Documentation

**Version:** 1.0.0
**Date:** March 11, 2026
**Project:** CuraMatrix HSM
**Base Package:** `com.curamatrix.hsm`

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Project Structure](#4-project-structure)
5. [Database Schema Design](#5-database-schema-design)
6. [Entity Relationship Diagram](#6-entity-relationship-diagram)
7. [Entity Definitions](#7-entity-definitions)
8. [Enum Definitions](#8-enum-definitions)
9. [Security and Authentication (JWT)](#9-security-and-authentication-jwt)
10. [Module Breakdown](#10-module-breakdown)
11. [REST API Specification](#11-rest-api-specification)
12. [DTO Definitions (Request / Response)](#12-dto-definitions-request--response)
13. [Service Layer Business Rules](#13-service-layer-business-rules)
14. [Exception Handling](#14-exception-handling)
15. [Configuration Files](#15-configuration-files)
16. [Implementation Phases](#16-implementation-phases)
17. [Testing Strategy](#17-testing-strategy)
18. [Appendix: SQL Schema Script](#18-appendix-sql-schema-script)

---

## 1. Project Overview

CuraMatrix HSM is a Hospital Management System backend built with Spring Boot. It supports three user roles:

| Role | Responsibilities |
|------|-----------------|
| **Receptionist** | Patient registration, appointment booking (scheduled + walk-in), billing |
| **Doctor** | View appointment queue, create diagnosis, write prescriptions |
| **Admin** | User management, department management, medicine inventory, reports & analytics |

**Key Features:**
- JWT-based stateless authentication with role-based access control
- Scheduled appointment booking with time slot management
- Walk-in patient queue with token system
- Full diagnosis and prescription workflow
- Billing and invoice generation
- Medicine inventory tracking with batch and expiry management
- Admin dashboard with reports (revenue, patient count, doctor performance)
- Swagger/OpenAPI documentation auto-generated

---

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 3.5.11 |
| Language | Java | 17 |
| Database | MySQL | 8.x |
| ORM | Spring Data JPA + Hibernate | (managed by Spring Boot) |
| Authentication | Spring Security + JWT (jjwt) | 0.12.x |
| API Docs | Springdoc OpenAPI | 2.x |
| Validation | Jakarta Bean Validation | (managed by Spring Boot) |
| DTO Mapping | MapStruct | 1.5.x |
| Build Tool | Gradle | 8.x |
| Utility | Lombok | (managed by Spring Boot) |

### Dependencies (build.gradle)

```groovy
dependencies {
    // Spring Boot Starters
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // Swagger / OpenAPI
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4'

    // MapStruct
    implementation 'org.mapstruct:mapstruct:1.5.5.Final'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

    // MySQL
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

## 3. Architecture Overview

The application follows a **layered architecture** pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Frontend)                        │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP (JSON)
┌──────────────────────────────▼──────────────────────────────────┐
│                    JWT AUTHENTICATION FILTER                     │
│              (Validates token, sets SecurityContext)             │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      CONTROLLER LAYER                           │
│  AuthController | PatientController | AppointmentController     │
│  DiagnosisController | PrescriptionController | BillingController│
│  AdminController | DepartmentController | MedicineController    │
│  InventoryController | ReportController                         │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                       SERVICE LAYER                             │
│  (Business logic, validation, orchestration)                    │
│  AuthService | PatientService | AppointmentService              │
│  DiagnosisService | PrescriptionService | BillingService        │
│  UserManagementService | DepartmentService | MedicineService    │
│  InventoryService | ReportService                               │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                     REPOSITORY LAYER                            │
│  (Spring Data JPA interfaces, custom queries)                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                     MySQL DATABASE                              │
│                   (13+ tables)                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Request Flow

```
Client Request
  → JwtAuthenticationFilter (extract & validate JWT)
  → SecurityContext (set authenticated user + roles)
  → @PreAuthorize / SecurityConfig (check role permissions)
  → Controller (parse request, call service)
  → Service (business logic, validations)
  → Repository (database operations)
  → Response DTO → JSON → Client
```

---

## 4. Project Structure

```
src/main/java/com/curamatrix/hsm/
├── HsmApplication.java                    # Main entry point
│
├── config/                                # Configuration classes
│   ├── SecurityConfig.java                # Spring Security + endpoint rules
│   ├── JwtAuthenticationFilter.java       # JWT filter (OncePerRequestFilter)
│   ├── JwtUtil.java                       # JWT token utility
│   ├── OpenApiConfig.java                 # Swagger configuration
│   └── CorsConfig.java                    # CORS configuration
│
├── controller/                            # REST controllers
│   ├── AuthController.java                # POST /api/auth/login, /refresh
│   ├── PatientController.java             # CRUD /api/patients
│   ├── AppointmentController.java         # /api/appointments
│   ├── DiagnosisController.java           # /api/diagnoses
│   ├── PrescriptionController.java        # /api/prescriptions
│   ├── BillingController.java             # /api/billings
│   ├── AdminController.java               # /api/admin/users
│   ├── DepartmentController.java          # /api/admin/departments
│   ├── MedicineController.java            # /api/medicines
│   ├── InventoryController.java           # /api/inventory
│   └── ReportController.java              # /api/reports
│
├── dto/
│   ├── request/                           # Incoming request bodies
│   │   ├── LoginRequest.java
│   │   ├── PatientRequest.java
│   │   ├── AppointmentRequest.java
│   │   ├── WalkInRequest.java
│   │   ├── StatusUpdateRequest.java
│   │   ├── DiagnosisRequest.java
│   │   ├── PrescriptionRequest.java
│   │   ├── BillingRequest.java
│   │   ├── BillingItemRequest.java
│   │   ├── PaymentUpdateRequest.java
│   │   ├── CreateUserRequest.java
│   │   ├── UpdateUserRequest.java
│   │   ├── DepartmentRequest.java
│   │   ├── MedicineRequest.java
│   │   └── InventoryRequest.java
│   │
│   ├── response/                          # Outgoing response bodies
│   │   ├── LoginResponse.java
│   │   ├── PatientResponse.java
│   │   ├── PatientDetailResponse.java
│   │   ├── AppointmentResponse.java
│   │   ├── SlotResponse.java
│   │   ├── DiagnosisResponse.java
│   │   ├── PrescriptionResponse.java
│   │   ├── BillingResponse.java
│   │   ├── BillingDetailResponse.java
│   │   ├── UserResponse.java
│   │   ├── DepartmentResponse.java
│   │   ├── DoctorResponse.java
│   │   ├── MedicineSearchDto.java         # (existing, keep)
│   │   ├── InventoryResponse.java
│   │   ├── DashboardResponse.java
│   │   ├── RevenueReportResponse.java
│   │   ├── DoctorPerformanceResponse.java
│   │   ├── ApiResponse.java               # Generic wrapper
│   │   ├── PagedResponse.java             # Pagination wrapper
│   │   └── ErrorResponse.java             # Error wrapper
│   │
│   └── MedicineSearchDto.java             # (existing, move to response/)
│
├── entity/                                # JPA entities
│   ├── User.java
│   ├── Role.java
│   ├── Department.java
│   ├── Doctor.java
│   ├── Receptionist.java
│   ├── Patient.java
│   ├── Appointment.java
│   ├── Diagnosis.java
│   ├── Prescription.java
│   ├── Medicine.java                      # (existing, extend)
│   ├── MedicineInventory.java
│   ├── Billing.java
│   └── BillingItem.java
│
├── enums/                                 # Enum types
│   ├── RoleName.java
│   ├── Gender.java
│   ├── BloodGroup.java
│   ├── AppointmentType.java
│   ├── AppointmentStatus.java
│   ├── Severity.java
│   ├── PaymentStatus.java
│   ├── PaymentMethod.java
│   ├── BillingItemType.java
│   └── Shift.java
│
├── exception/                             # Exception handling
│   ├── GlobalExceptionHandler.java        # @RestControllerAdvice
│   ├── ResourceNotFoundException.java
│   ├── DuplicateResourceException.java
│   ├── BadRequestException.java
│   ├── UnauthorizedException.java
│   └── SlotNotAvailableException.java
│
├── helper/                                # Mappers
│   ├── PatientMapper.java                 # MapStruct interface
│   ├── AppointmentMapper.java
│   ├── DiagnosisMapper.java
│   ├── PrescriptionMapper.java
│   ├── BillingMapper.java
│   ├── UserMapper.java
│   ├── DepartmentMapper.java
│   ├── MedicineMapper.java                # (existing, convert to MapStruct)
│   └── InventoryMapper.java
│
├── repository/                            # Spring Data JPA repos
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── DoctorRepository.java
│   ├── ReceptionistRepository.java
│   ├── PatientRepository.java
│   ├── AppointmentRepository.java
│   ├── DiagnosisRepository.java
│   ├── PrescriptionRepository.java
│   ├── MedicineRepository.java            # (existing, extend)
│   ├── MedicineInventoryRepository.java
│   ├── BillingRepository.java
│   ├── BillingItemRepository.java
│   └── DepartmentRepository.java
│
└── service/                               # Business logic
    ├── AuthService.java
    ├── CustomUserDetailsService.java
    ├── PatientService.java
    ├── AppointmentService.java
    ├── DiagnosisService.java
    ├── PrescriptionService.java
    ├── BillingService.java
    ├── UserManagementService.java
    ├── DepartmentService.java
    ├── MedicineService.java               # (existing, extend)
    ├── InventoryService.java
    └── ReportService.java
```

---

## 5. Database Schema Design

### 5.1 Table: `users`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Unique user ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | Login email |
| password | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| full_name | VARCHAR(255) | NOT NULL | Display name |
| phone | VARCHAR(20) | | Contact number |
| is_active | BOOLEAN | DEFAULT TRUE | Soft delete flag |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Record creation time |
| updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | Last update time |

### 5.2 Table: `roles`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Role ID |
| name | VARCHAR(50) | UNIQUE, NOT NULL | ROLE_ADMIN, ROLE_DOCTOR, ROLE_RECEPTIONIST |

### 5.3 Table: `user_roles` (Join Table)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| user_id | BIGINT | FK → users.id | User reference |
| role_id | BIGINT | FK → roles.id | Role reference |
| | | PK(user_id, role_id) | Composite primary key |

### 5.4 Table: `departments`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Department ID |
| name | VARCHAR(255) | NOT NULL | Department name (e.g., Cardiology) |
| description | VARCHAR(500) | | Description |
| is_active | BOOLEAN | DEFAULT TRUE | Active flag |

### 5.5 Table: `doctors`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Doctor profile ID |
| user_id | BIGINT | FK → users.id, UNIQUE | Linked user account |
| department_id | BIGINT | FK → departments.id | Department |
| specialization | VARCHAR(255) | NOT NULL | Medical specialization |
| license_number | VARCHAR(100) | UNIQUE, NOT NULL | Medical license |
| qualification | VARCHAR(255) | | Degrees (e.g., MBBS, MD) |
| experience_years | INT | | Years of experience |
| consultation_fee | DECIMAL(10,2) | NOT NULL | Fee per consultation |

### 5.6 Table: `receptionists`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Receptionist profile ID |
| user_id | BIGINT | FK → users.id, UNIQUE | Linked user account |
| employee_id | VARCHAR(50) | UNIQUE | Employee code |
| shift | VARCHAR(20) | | MORNING, AFTERNOON, NIGHT |

### 5.7 Table: `patients`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Patient ID |
| first_name | VARCHAR(100) | NOT NULL | First name |
| last_name | VARCHAR(100) | NOT NULL | Last name |
| date_of_birth | DATE | NOT NULL | DOB |
| gender | VARCHAR(10) | NOT NULL | MALE, FEMALE, OTHER |
| phone | VARCHAR(20) | NOT NULL | Primary contact |
| email | VARCHAR(255) | | Email (optional) |
| address | TEXT | | Full address |
| blood_group | VARCHAR(5) | | A+, B-, O+, AB+, etc. |
| emergency_contact_name | VARCHAR(255) | | Emergency contact person |
| emergency_contact_phone | VARCHAR(20) | | Emergency contact number |
| allergies | TEXT | | Known allergies |
| medical_history | TEXT | | Pre-existing conditions |
| registered_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Registration time |
| registered_by | BIGINT | FK → users.id | Receptionist who registered |

### 5.8 Table: `appointments`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Appointment ID |
| patient_id | BIGINT | FK → patients.id, NOT NULL | Patient |
| doctor_id | BIGINT | FK → doctors.id, NOT NULL | Assigned doctor |
| booked_by | BIGINT | FK → users.id, NOT NULL | Receptionist who booked |
| appointment_date | DATE | NOT NULL | Date of appointment |
| appointment_time | TIME | | Time slot (null for walk-in) |
| type | VARCHAR(20) | NOT NULL | SCHEDULED, WALK_IN |
| token_number | INT | | Walk-in token (auto-generated per day) |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'BOOKED' | BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED |
| notes | TEXT | | Additional notes |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Booking time |

### 5.9 Table: `diagnoses`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Diagnosis ID |
| appointment_id | BIGINT | FK → appointments.id, UNIQUE | One diagnosis per appointment |
| doctor_id | BIGINT | FK → doctors.id, NOT NULL | Diagnosing doctor |
| symptoms | TEXT | NOT NULL | Patient symptoms |
| diagnosis | TEXT | NOT NULL | Doctor's diagnosis |
| clinical_notes | TEXT | | Additional clinical notes |
| severity | VARCHAR(20) | | MILD, MODERATE, SEVERE, CRITICAL |
| follow_up_date | DATE | | Recommended follow-up date |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Diagnosis time |

### 5.10 Table: `prescriptions`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Prescription line ID |
| diagnosis_id | BIGINT | FK → diagnoses.id, NOT NULL | Parent diagnosis |
| medicine_id | BIGINT | FK → medicines.id, NOT NULL | Prescribed medicine |
| dosage | VARCHAR(100) | NOT NULL | e.g., "500mg" |
| frequency | VARCHAR(100) | NOT NULL | e.g., "Twice daily" |
| duration_days | INT | NOT NULL | Number of days |
| instructions | TEXT | | e.g., "Take after meals" |

### 5.11 Table: `medicines` (existing -- extend)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Medicine ID |
| name | VARCHAR(255) | NOT NULL | Medicine name |
| generic_name | VARCHAR(255) | | Generic/salt name |
| brand | VARCHAR(255) | | Brand name |
| strength | VARCHAR(50) | | e.g., "500mg" |
| form | VARCHAR(50) | | TABLET, CAPSULE, SYRUP, INJECTION, etc. |
| category | VARCHAR(100) | | **NEW** -- Antibiotic, Painkiller, etc. |
| is_active | BOOLEAN | DEFAULT TRUE | **NEW** -- Active flag |

### 5.12 Table: `medicine_inventory`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Inventory record ID |
| medicine_id | BIGINT | FK → medicines.id, NOT NULL | Medicine reference |
| quantity | INT | NOT NULL, DEFAULT 0 | Stock quantity |
| unit_price | DECIMAL(10,2) | NOT NULL | Price per unit |
| expiry_date | DATE | NOT NULL | Batch expiry date |
| batch_number | VARCHAR(100) | | Batch identifier |
| last_updated | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | Last stock update |

### 5.13 Table: `billings`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Billing ID |
| appointment_id | BIGINT | FK → appointments.id | Related appointment |
| patient_id | BIGINT | FK → patients.id, NOT NULL | Patient |
| invoice_number | VARCHAR(50) | UNIQUE, NOT NULL | Auto-generated (e.g., INV-20260311-001) |
| total_amount | DECIMAL(12,2) | NOT NULL | Sum of all items |
| discount | DECIMAL(10,2) | DEFAULT 0 | Discount amount |
| tax | DECIMAL(10,2) | DEFAULT 0 | Tax amount |
| net_amount | DECIMAL(12,2) | NOT NULL | total - discount + tax |
| payment_status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING, PAID, PARTIAL, CANCELLED |
| payment_method | VARCHAR(20) | | CASH, CARD, UPI, INSURANCE |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Invoice date |
| created_by | BIGINT | FK → users.id | Receptionist who created |

### 5.14 Table: `billing_items`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | Line item ID |
| billing_id | BIGINT | FK → billings.id, NOT NULL | Parent billing |
| description | VARCHAR(255) | NOT NULL | Item description |
| amount | DECIMAL(10,2) | NOT NULL | Unit price |
| quantity | INT | NOT NULL, DEFAULT 1 | Quantity |
| item_type | VARCHAR(20) | NOT NULL | CONSULTATION, LAB, MEDICINE, PROCEDURE |

---

## 6. Entity Relationship Diagram

```
                    ┌──────────┐
                    │  roles   │
                    └────┬─────┘
                         │ M:N
                    ┌────▼─────┐
              ┌─────┤  users   ├─────┐
              │     └────┬─────┘     │
              │ 1:1      │ 1:1       │
        ┌─────▼───┐  ┌───▼──────┐   │
        │ doctors  │  │reception-│   │
        │          │  │  ists    │   │
        └─────┬────┘  └─────────┘   │
              │                      │
    ┌─────────┤                      │ registered_by
    │ dept_id │                      │
┌───▼────┐    │              ┌───────▼──┐
│depart- │    │              │ patients  │
│ments   │    │              └─────┬─────┘
└────────┘    │                    │
              │ doctor_id          │ patient_id
              │                    │
         ┌────▼────────────────────▼───┐
         │        appointments         │
         └──────────────┬──────────────┘
                        │ 1:1
                  ┌─────▼──────┐
                  │ diagnoses  │
                  └─────┬──────┘
                        │ 1:N
                ┌───────▼────────┐
                │ prescriptions  │──────┐
                └────────────────┘      │ medicine_id
                                  ┌─────▼─────┐
                                  │ medicines  │
                                  └─────┬──────┘
                                        │ 1:N
                                ┌───────▼────────┐
                                │medicine_inventory│
                                └────────────────┘

         ┌──────────────────────────────┐
         │        appointments          │
         └──────────────┬───────────────┘
                        │ 1:1
                  ┌─────▼──────┐
                  │  billings  │
                  └─────┬──────┘
                        │ 1:N
                ┌───────▼────────┐
                │ billing_items  │
                └────────────────┘
```

**Relationships Summary:**

| From | To | Type | FK Column |
|------|----|------|-----------|
| users | roles | Many-to-Many | user_roles (join table) |
| users | doctors | One-to-One | doctors.user_id |
| users | receptionists | One-to-One | receptionists.user_id |
| departments | doctors | One-to-Many | doctors.department_id |
| patients | appointments | One-to-Many | appointments.patient_id |
| doctors | appointments | One-to-Many | appointments.doctor_id |
| appointments | diagnoses | One-to-One | diagnoses.appointment_id |
| diagnoses | prescriptions | One-to-Many | prescriptions.diagnosis_id |
| medicines | prescriptions | One-to-Many | prescriptions.medicine_id |
| medicines | medicine_inventory | One-to-Many | medicine_inventory.medicine_id |
| appointments | billings | One-to-One | billings.appointment_id |
| billings | billing_items | One-to-Many | billing_items.billing_id |
| users | patients | One-to-Many | patients.registered_by |
| users | billings | One-to-Many | billings.created_by |
| users | appointments | One-to-Many | appointments.booked_by |

---

## 7. Entity Definitions

### 7.1 User Entity

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### 7.2 Role Entity

```java
@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private RoleName name;
}
```

### 7.3 Doctor Entity

```java
@Entity
@Table(name = "doctors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private String specialization;

    @Column(name = "license_number", unique = true, nullable = false)
    private String licenseNumber;

    private String qualification;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "consultation_fee", nullable = false)
    private BigDecimal consultationFee;
}
```

### 7.4 Patient Entity

```java
@Entity
@Table(name = "patients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private String phone;

    private String email;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    private BloodGroup bloodGroup;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "medical_history", columnDefinition = "TEXT")
    private String medicalHistory;

    @Column(name = "registered_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime registeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by")
    private User registeredBy;
}
```

### 7.5 Appointment Entity

```java
@Entity
@Table(name = "appointments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by", nullable = false)
    private User bookedBy;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType type;

    @Column(name = "token_number")
    private Integer tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 7.6 Diagnosis Entity

```java
@Entity
@Table(name = "diagnoses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true, nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String symptoms;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String diagnosis;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prescription> prescriptions = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 7.7 Prescription Entity

```java
@Entity
@Table(name = "prescriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private Diagnosis diagnosis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Column(nullable = false)
    private String dosage;

    @Column(nullable = false)
    private String frequency;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(columnDefinition = "TEXT")
    private String instructions;
}
```

### 7.8 Billing Entity

```java
@Entity
@Table(name = "billings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillingItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
```

---

## 8. Enum Definitions

```java
public enum RoleName {
    ROLE_ADMIN, ROLE_DOCTOR, ROLE_RECEPTIONIST
}

public enum Gender {
    MALE, FEMALE, OTHER
}

public enum BloodGroup {
    A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE,
    O_POSITIVE, O_NEGATIVE, AB_POSITIVE, AB_NEGATIVE
}

public enum AppointmentType {
    SCHEDULED, WALK_IN
}

public enum AppointmentStatus {
    BOOKED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED
}

public enum Severity {
    MILD, MODERATE, SEVERE, CRITICAL
}

public enum PaymentStatus {
    PENDING, PAID, PARTIAL, CANCELLED
}

public enum PaymentMethod {
    CASH, CARD, UPI, INSURANCE
}

public enum BillingItemType {
    CONSULTATION, LAB, MEDICINE, PROCEDURE
}

public enum Shift {
    MORNING, AFTERNOON, NIGHT
}
```

---

## 9. Security and Authentication (JWT)

### 9.1 JWT Flow

```
1. Client sends POST /api/auth/login with { email, password }
2. AuthService validates credentials against DB (BCrypt)
3. JwtUtil generates a signed JWT containing:
   - subject: user email
   - claims: userId, roles, fullName
   - expiry: 24 hours (configurable)
4. Client receives { token, tokenType, role, fullName, expiresIn }
5. Client sends subsequent requests with header:
   Authorization: Bearer <token>
6. JwtAuthenticationFilter intercepts every request:
   - Extracts token from Authorization header
   - Validates signature and expiry
   - Extracts user details and roles
   - Sets SecurityContext with authenticated principal
7. @PreAuthorize or SecurityConfig checks role-based access
```

### 9.2 JWT Token Structure

```json
{
  "header": {
    "alg": "HS512",
    "typ": "JWT"
  },
  "payload": {
    "sub": "doctor@hospital.com",
    "userId": 5,
    "roles": ["ROLE_DOCTOR"],
    "fullName": "Dr. Smith",
    "iat": 1741651200,
    "exp": 1741737600
  }
}
```

### 9.3 Endpoint Security Rules

```java
// SecurityConfig.java - endpoint access rules
http.authorizeHttpRequests(auth -> auth
    // Public
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

    // Receptionist endpoints
    .requestMatchers(HttpMethod.POST, "/api/patients/**").hasRole("RECEPTIONIST")
    .requestMatchers(HttpMethod.PUT, "/api/patients/**").hasRole("RECEPTIONIST")
    .requestMatchers(HttpMethod.POST, "/api/appointments/**").hasRole("RECEPTIONIST")
    .requestMatchers(HttpMethod.POST, "/api/billings/**").hasRole("RECEPTIONIST")
    .requestMatchers(HttpMethod.PUT, "/api/billings/*/payment").hasRole("RECEPTIONIST")

    // Doctor endpoints
    .requestMatchers("/api/diagnoses/**").hasRole("DOCTOR")
    .requestMatchers("/api/prescriptions/**").hasRole("DOCTOR")
    .requestMatchers(HttpMethod.GET, "/api/appointments/doctor/**").hasRole("DOCTOR")

    // Shared read access
    .requestMatchers(HttpMethod.GET, "/api/patients/**").hasAnyRole("RECEPTIONIST", "DOCTOR")
    .requestMatchers(HttpMethod.GET, "/api/appointments/**").hasAnyRole("RECEPTIONIST", "DOCTOR")

    // Admin endpoints
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/inventory/**").hasRole("ADMIN")
    .requestMatchers("/api/reports/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/medicines/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.PUT, "/api/medicines/**").hasRole("ADMIN")

    // Medicine search - doctor access
    .requestMatchers(HttpMethod.GET, "/api/medicines/**").hasAnyRole("DOCTOR", "ADMIN")

    .anyRequest().authenticated()
);
```

### 9.4 application.yml JWT Config

```yaml
app:
  jwt:
    secret: "your-256-bit-secret-key-here-must-be-at-least-64-chars-long-for-hs512"
    expiration-ms: 86400000   # 24 hours
    refresh-expiration-ms: 604800000  # 7 days
```

---

## 10. Module Breakdown

### 10.1 Authentication Module

| Component | Responsibility |
|-----------|---------------|
| `AuthController` | Login and token refresh endpoints |
| `AuthService` | Credential validation, token generation |
| `CustomUserDetailsService` | Load user from DB for Spring Security |
| `JwtUtil` | Generate, validate, parse JWT tokens |
| `JwtAuthenticationFilter` | Intercept requests, validate JWT, set SecurityContext |

### 10.2 Receptionist Module

| Component | Responsibility |
|-----------|---------------|
| `PatientController` | Patient CRUD endpoints |
| `PatientService` | Register patient, search, update, validate uniqueness |
| `AppointmentController` | Booking and queue endpoints |
| `AppointmentService` | Book scheduled, create walk-in token, check slot availability, update status |
| `BillingController` | Invoice endpoints |
| `BillingService` | Generate invoice, calculate totals, update payment |

**Walk-in Token Logic:**
- Token number auto-increments per doctor per day
- Query: `SELECT MAX(token_number) FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND type = 'WALK_IN'`
- New token = max + 1 (or 1 if none exist)

**Slot Availability Logic:**
- Doctor's working hours: 09:00 - 17:00 (configurable)
- Slot duration: 30 minutes (configurable)
- Generate all slots, subtract booked slots for the date
- Return available time slots

### 10.3 Doctor Module

| Component | Responsibility |
|-----------|---------------|
| `DiagnosisController` | Diagnosis CRUD endpoints |
| `DiagnosisService` | Create/update diagnosis, link to appointment |
| `PrescriptionController` | Prescription CRUD endpoints |
| `PrescriptionService` | Add/update/remove prescriptions, medicine search |
| `MedicineController` | Medicine search endpoint (existing, extend) |
| `MedicineService` | Search medicines (existing, extend) |

**Doctor Queue Flow:**
1. Doctor calls `GET /api/appointments/doctor/{id}/today`
2. Returns appointments sorted by token_number (walk-in) and appointment_time (scheduled)
3. Doctor updates status to IN_PROGRESS when starting consultation
4. Doctor creates diagnosis and prescriptions
5. Doctor updates status to COMPLETED

### 10.4 Admin Module

| Component | Responsibility |
|-----------|---------------|
| `AdminController` | User management endpoints |
| `UserManagementService` | Create/update/deactivate users, assign roles |
| `DepartmentController` | Department CRUD |
| `DepartmentService` | Department management |
| `InventoryController` | Stock management endpoints |
| `InventoryService` | Add/update stock, track expiry |
| `ReportController` | Analytics endpoints |
| `ReportService` | Dashboard stats, revenue, performance queries |

**Invoice Number Generation:**
- Format: `INV-YYYYMMDD-NNN`
- Example: `INV-20260311-001`
- Auto-increment NNN per day

---

## 11. REST API Specification

### 11.1 Authentication

#### POST /api/auth/login

**Request:**
```json
{
  "email": "doctor@hospital.com",
  "password": "password123"
}
```

**Response (200):**
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

**Error (401):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "timestamp": "2026-03-11T10:30:00"
}
```

---

### 11.2 Patient APIs

#### POST /api/patients -- Register Patient

**Access:** RECEPTIONIST

**Request:**
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

**Response (201):**
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
    "bloodGroup": "B_POSITIVE",
    "registeredAt": "2026-03-11T10:30:00"
  }
}
```

#### GET /api/patients -- List Patients (Paginated)

**Access:** RECEPTIONIST, DOCTOR

**Query Params:** `?page=0&size=20&search=amit&sortBy=firstName&sortDir=asc`

**Response (200):**
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

#### GET /api/patients/{id} -- Patient Details

**Access:** RECEPTIONIST, DOCTOR

**Response (200):**
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

#### GET /api/patients/{id}/history -- Patient Medical History

**Access:** DOCTOR

**Response (200):**
```json
{
  "patient": {
    "id": 101,
    "firstName": "Amit",
    "lastName": "Sharma"
  },
  "visits": [
    {
      "appointmentId": 501,
      "date": "2026-03-01",
      "doctorName": "Dr. Rajesh Kumar",
      "department": "General Medicine",
      "diagnosis": "Viral Fever",
      "severity": "MILD",
      "prescriptions": [
        {
          "medicineName": "Paracetamol 500mg",
          "dosage": "500mg",
          "frequency": "Thrice daily",
          "durationDays": 5
        }
      ]
    }
  ]
}
```

---

### 11.3 Appointment APIs

#### POST /api/appointments -- Book Scheduled Appointment

**Access:** RECEPTIONIST

**Request:**
```json
{
  "patientId": 101,
  "doctorId": 5,
  "appointmentDate": "2026-03-15",
  "appointmentTime": "10:30",
  "notes": "Follow-up for diabetes"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Appointment booked successfully",
  "data": {
    "id": 502,
    "patientName": "Amit Sharma",
    "doctorName": "Dr. Rajesh Kumar",
    "appointmentDate": "2026-03-15",
    "appointmentTime": "10:30",
    "type": "SCHEDULED",
    "status": "BOOKED"
  }
}
```

#### POST /api/appointments/walk-in -- Walk-in Token

**Access:** RECEPTIONIST

**Request:**
```json
{
  "patientId": 101,
  "doctorId": 5,
  "notes": "Fever and headache"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Walk-in token generated",
  "data": {
    "id": 503,
    "patientName": "Amit Sharma",
    "doctorName": "Dr. Rajesh Kumar",
    "appointmentDate": "2026-03-11",
    "type": "WALK_IN",
    "tokenNumber": 7,
    "status": "BOOKED"
  }
}
```

#### GET /api/appointments/doctor/{doctorId}/today -- Doctor's Queue

**Access:** DOCTOR

**Response (200):**
```json
[
  {
    "id": 500,
    "tokenNumber": 1,
    "type": "WALK_IN",
    "patientName": "Ravi Patel",
    "patientId": 99,
    "status": "CHECKED_IN",
    "notes": "Cough and cold"
  },
  {
    "id": 502,
    "appointmentTime": "10:30",
    "type": "SCHEDULED",
    "patientName": "Amit Sharma",
    "patientId": 101,
    "status": "BOOKED",
    "notes": "Follow-up for diabetes"
  }
]
```

#### GET /api/appointments/doctor/{doctorId}/slots?date=2026-03-15

**Access:** RECEPTIONIST

**Response (200):**
```json
{
  "doctorId": 5,
  "doctorName": "Dr. Rajesh Kumar",
  "date": "2026-03-15",
  "availableSlots": [
    "09:00", "09:30", "10:00", "11:00", "11:30",
    "14:00", "14:30", "15:00", "15:30", "16:00", "16:30"
  ],
  "bookedSlots": [
    "10:30", "12:00", "13:00", "13:30"
  ]
}
```

#### PUT /api/appointments/{id}/status -- Update Status

**Access:** RECEPTIONIST, DOCTOR

**Request:**
```json
{
  "status": "IN_PROGRESS"
}
```

---

### 11.4 Diagnosis APIs

#### POST /api/diagnoses -- Create Diagnosis

**Access:** DOCTOR

**Request:**
```json
{
  "appointmentId": 502,
  "symptoms": "High blood sugar levels, frequent urination, fatigue",
  "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
  "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes and medication adjustment.",
  "severity": "MODERATE",
  "followUpDate": "2026-04-15"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Diagnosis created successfully",
  "data": {
    "id": 301,
    "appointmentId": 502,
    "patientName": "Amit Sharma",
    "symptoms": "High blood sugar levels, frequent urination, fatigue",
    "diagnosis": "Uncontrolled Type 2 Diabetes Mellitus",
    "clinicalNotes": "HbA1c: 8.5%. Recommend dietary changes and medication adjustment.",
    "severity": "MODERATE",
    "followUpDate": "2026-04-15",
    "createdAt": "2026-03-11T11:00:00"
  }
}
```

---

### 11.5 Prescription APIs

#### POST /api/prescriptions -- Add Prescriptions

**Access:** DOCTOR

**Request:**
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

**Response (201):**
```json
{
  "success": true,
  "message": "Prescriptions added successfully",
  "data": [
    {
      "id": 401,
      "medicineName": "Metformin",
      "medicineStrength": "500mg",
      "dosage": "500mg",
      "frequency": "Twice daily",
      "durationDays": 30,
      "instructions": "Take after meals"
    },
    {
      "id": 402,
      "medicineName": "Glimepiride",
      "medicineStrength": "5mg",
      "dosage": "5mg",
      "frequency": "Once daily (morning)",
      "durationDays": 30,
      "instructions": "Take before breakfast"
    }
  ]
}
```

---

### 11.6 Billing APIs

#### POST /api/billings -- Generate Bill

**Access:** RECEPTIONIST

**Request:**
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

**Response (201):**
```json
{
  "success": true,
  "message": "Bill generated successfully",
  "data": {
    "id": 601,
    "invoiceNumber": "INV-20260311-001",
    "patientName": "Amit Sharma",
    "totalAmount": 970.00,
    "discount": 100.00,
    "tax": 50.00,
    "netAmount": 920.00,
    "paymentStatus": "PENDING",
    "paymentMethod": "UPI",
    "items": [ ... ],
    "createdAt": "2026-03-11T12:00:00"
  }
}
```

#### PUT /api/billings/{id}/payment -- Update Payment

**Access:** RECEPTIONIST

**Request:**
```json
{
  "paymentStatus": "PAID",
  "paymentMethod": "UPI"
}
```

---

### 11.7 Admin APIs

#### POST /api/admin/users -- Create User

**Access:** ADMIN

**Request (Create Doctor):**
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

**Request (Create Receptionist):**
```json
{
  "email": "reception2@hospital.com",
  "password": "tempPassword123",
  "fullName": "Neha Gupta",
  "phone": "9876543333",
  "role": "ROLE_RECEPTIONIST",
  "receptionistDetails": {
    "employeeId": "REC-002",
    "shift": "MORNING"
  }
}
```

---

### 11.8 Report APIs

#### GET /api/reports/dashboard

**Access:** ADMIN

**Response (200):**
```json
{
  "totalPatients": 1250,
  "totalDoctors": 25,
  "todayAppointments": 47,
  "todayRevenue": 35600.00,
  "pendingBills": 12,
  "monthlyRevenue": 856000.00,
  "appointmentsByStatus": {
    "BOOKED": 15,
    "CHECKED_IN": 8,
    "IN_PROGRESS": 5,
    "COMPLETED": 19
  }
}
```

#### GET /api/reports/revenue?from=2026-03-01&to=2026-03-11

**Access:** ADMIN

**Response (200):**
```json
{
  "from": "2026-03-01",
  "to": "2026-03-11",
  "totalRevenue": 425000.00,
  "totalBills": 312,
  "dailyBreakdown": [
    { "date": "2026-03-01", "revenue": 38500.00, "billCount": 28 },
    { "date": "2026-03-02", "revenue": 42000.00, "billCount": 31 }
  ]
}
```

#### GET /api/reports/doctors/performance?from=2026-03-01&to=2026-03-11

**Access:** ADMIN

**Response (200):**
```json
[
  {
    "doctorId": 5,
    "doctorName": "Dr. Rajesh Kumar",
    "department": "General Medicine",
    "totalAppointments": 85,
    "completedAppointments": 78,
    "cancelledAppointments": 3,
    "totalRevenue": 62400.00,
    "averageRating": null
  }
]
```

---

## 12. DTO Definitions (Request / Response)

### 12.1 Generic Wrappers

```java
// ApiResponse.java - wraps all successful responses
@Getter @Setter @Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}

// PagedResponse.java - wraps paginated results
@Getter @Setter @Builder
public class PagedResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
}

// ErrorResponse.java - wraps error responses
@Getter @Setter @Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> validationErrors;  // for 400 errors
}
```

### 12.2 Request DTOs

All request DTOs use Jakarta Validation annotations:

```java
// LoginRequest.java
@Getter @Setter
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

// PatientRequest.java
@Getter @Setter
public class PatientRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotNull private LocalDate dateOfBirth;
    @NotNull private Gender gender;
    @NotBlank @Pattern(regexp = "^[0-9]{10}$") private String phone;
    @Email private String email;
    private String address;
    private BloodGroup bloodGroup;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String allergies;
    private String medicalHistory;
}

// AppointmentRequest.java
@Getter @Setter
public class AppointmentRequest {
    @NotNull private Long patientId;
    @NotNull private Long doctorId;
    @NotNull private LocalDate appointmentDate;
    @NotNull private LocalTime appointmentTime;
    private String notes;
}

// WalkInRequest.java
@Getter @Setter
public class WalkInRequest {
    @NotNull private Long patientId;
    @NotNull private Long doctorId;
    private String notes;
}

// DiagnosisRequest.java
@Getter @Setter
public class DiagnosisRequest {
    @NotNull private Long appointmentId;
    @NotBlank private String symptoms;
    @NotBlank private String diagnosis;
    private String clinicalNotes;
    private Severity severity;
    private LocalDate followUpDate;
}

// PrescriptionRequest.java (batch)
@Getter @Setter
public class PrescriptionRequest {
    @NotNull private Long diagnosisId;
    @NotEmpty private List<PrescriptionItemRequest> prescriptions;
}

@Getter @Setter
public class PrescriptionItemRequest {
    @NotNull private Long medicineId;
    @NotBlank private String dosage;
    @NotBlank private String frequency;
    @NotNull private Integer durationDays;
    private String instructions;
}
```

---

## 13. Service Layer Business Rules

### 13.1 Patient Registration
- Phone number must be unique (warn if duplicate, don't block)
- `registeredBy` is auto-set from JWT authenticated user
- All fields validated via Jakarta Bean Validation

### 13.2 Appointment Booking
- **Scheduled:** Validate that the time slot is available for the doctor on that date
- **Walk-in:** Auto-generate token number (max token for doctor+date + 1)
- Cannot book appointment for past dates
- Cannot double-book same patient with same doctor on same date+time
- Appointment date must be within 30 days from today

### 13.3 Appointment Status Transitions
```
BOOKED → CHECKED_IN → IN_PROGRESS → COMPLETED
BOOKED → CANCELLED
CHECKED_IN → CANCELLED
```
- Only valid transitions allowed; others throw `BadRequestException`
- COMPLETED and CANCELLED are terminal states

### 13.4 Diagnosis
- One diagnosis per appointment (enforced by UNIQUE constraint)
- Appointment must be in IN_PROGRESS status
- Only the assigned doctor can create diagnosis for their appointment
- After diagnosis, appointment can be marked COMPLETED

### 13.5 Prescription
- Must be linked to a valid diagnosis
- Medicine must exist and be active
- Doctor can add multiple prescriptions at once (batch)

### 13.6 Billing
- Invoice number auto-generated: `INV-YYYYMMDD-NNN`
- `netAmount = totalAmount - discount + tax`
- `totalAmount = SUM(items.amount * items.quantity)`
- Payment status transitions: PENDING → PAID, PENDING → PARTIAL → PAID, any → CANCELLED

### 13.7 Inventory
- Stock quantity cannot go below 0
- Expiry date alerts: flag medicines expiring within 30 days
- Batch number tracking for traceability

---

## 14. Exception Handling

### GlobalExceptionHandler.java

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) { ... }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(DuplicateResourceException ex) { ... }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        // Extract field errors into map
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) { ... }

    @ExceptionHandler(SlotNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSlotConflict(SlotNotAvailableException ex) { ... }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) { ... }
}
```

### HTTP Status Code Convention

| Status | When |
|--------|------|
| 200 | Successful GET, PUT |
| 201 | Successful POST (resource created) |
| 204 | Successful DELETE |
| 400 | Validation errors, bad request |
| 401 | Invalid/missing JWT |
| 403 | Valid JWT but insufficient role |
| 404 | Resource not found |
| 409 | Duplicate resource, slot conflict |
| 500 | Unexpected server error |

---

## 15. Configuration Files

### application.yml (complete)

```yaml
spring:
  application:
    name: hsm

  datasource:
    url: jdbc:mysql://localhost:3306/hospitalsystems
    username: root
    password:
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true

server:
  port: 8080

app:
  jwt:
    secret: "curamatrix-hsm-secret-key-must-be-at-least-64-characters-long-for-hs512-algorithm"
    expiration-ms: 86400000
    refresh-expiration-ms: 604800000

  appointment:
    slot-duration-minutes: 30
    working-hours-start: "09:00"
    working-hours-end: "17:00"
    max-advance-booking-days: 30

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
```

---

## 16. Implementation Phases

| Phase | Description | Estimated Files | Priority |
|-------|-------------|----------------|----------|
| 1 | Dependencies, config, enums | ~12 files | P0 |
| 2 | All JPA entities | ~13 files | P0 |
| 3 | JWT auth + security | ~6 files | P0 |
| 4 | Receptionist module (patient, appointment) | ~10 files | P0 |
| 5 | Doctor module (diagnosis, prescription) | ~8 files | P0 |
| 6 | Billing module | ~6 files | P1 |
| 7 | Admin module (users, departments, inventory) | ~8 files | P1 |
| 8 | Reports, exception handler, Swagger | ~5 files | P2 |

**Total: ~68 files**

---

## 17. Testing Strategy

| Layer | Tool | What to Test |
|-------|------|-------------|
| Unit (Service) | JUnit 5 + Mockito | Business logic, validations, edge cases |
| Unit (Controller) | @WebMvcTest + MockMvc | Request/response mapping, status codes, security |
| Integration | @SpringBootTest + TestContainers | Full flow with real MySQL |
| Repository | @DataJpaTest | Custom queries, pagination |
| Security | @WithMockUser | Role-based access, JWT validation |

---

## 18. Appendix: SQL Schema Script

```sql
-- Run this to initialize the database and seed roles

CREATE DATABASE IF NOT EXISTS hospitalsystems;
USE hospitalsystems;

-- Seed roles (run once after first startup)
INSERT INTO roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_DOCTOR'), ('ROLE_RECEPTIONIST');

-- Seed admin user (password: admin123 - BCrypt hash)
INSERT INTO users (email, password, full_name, phone, is_active, created_at)
VALUES ('admin@curamatrix.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'System Admin', '9999999999', true, NOW());

-- Link admin user to ROLE_ADMIN
INSERT INTO user_roles (user_id, role_id)
VALUES ((SELECT id FROM users WHERE email = 'admin@curamatrix.com'),
        (SELECT id FROM roles WHERE name = 'ROLE_ADMIN'));

-- Seed departments
INSERT INTO departments (name, description, is_active) VALUES
('General Medicine', 'General health consultations', true),
('Cardiology', 'Heart and cardiovascular system', true),
('Orthopedics', 'Bones, joints, and muscles', true),
('Pediatrics', 'Child healthcare', true),
('Dermatology', 'Skin, hair, and nails', true),
('ENT', 'Ear, Nose, and Throat', true),
('Ophthalmology', 'Eye care', true),
('Gynecology', 'Women health', true),
('Neurology', 'Brain and nervous system', true),
('Dentistry', 'Dental care', true);
```

---

**End of Backend Engineer Documentation**
**CuraMatrix HSM v1.0.0**
