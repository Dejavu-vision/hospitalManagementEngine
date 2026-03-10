# User Setup Guide

## Problem: "Encoded password does not look like BCrypt"

This error occurs when the password in the database is not BCrypt-encoded. Here are the solutions:

---

## Solution 1: Use the Seed Data (Recommended)

The `docs/schema.sql` file already contains 3 users with BCrypt-encoded passwords:

```bash
mysql -u root -p < docs/schema.sql
```

**Default Credentials:**
- Admin: `admin@curamatrix.com` / `admin123`
- Doctor: `doctor@curamatrix.com` / `doctor123`
- Receptionist: `reception@curamatrix.com` / `reception123`

---

## Solution 2: Add Users via API (After Login as Admin)

### Step 1: Login as Admin
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "admin@curamatrix.com",
  "password": "admin123"
}
```

Save the JWT token from the response.

### Step 2: Create a Doctor User
```bash
POST http://localhost:8080/api/admin/users
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "email": "dr-prathmesh@curamatrix.com",
  "password": "doctor123",
  "fullName": "Dr. Prathmesh Sharma",
  "phone": "9876543212",
  "role": "ROLE_DOCTOR",
  "departmentId": 1,
  "specialization": "General Medicine",
  "licenseNumber": "MCI-GM-002",
  "qualification": "MBBS, MD",
  "experienceYears": 8,
  "consultationFee": 600.00
}
```

### Step 3: Create a Receptionist User
```bash
POST http://localhost:8080/api/admin/users
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "email": "receptionist2@curamatrix.com",
  "password": "reception123",
  "fullName": "Priya Patel",
  "phone": "9876543213",
  "role": "ROLE_RECEPTIONIST",
  "employeeId": "REC-002",
  "shift": "AFTERNOON"
}
```

### Step 4: Create an Admin User
```bash
POST http://localhost:8080/api/admin/users
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "email": "admin2@curamatrix.com",
  "password": "admin123",
  "fullName": "System Administrator 2",
  "phone": "9999999998",
  "role": "ROLE_ADMIN"
}
```

---

## Solution 3: Generate BCrypt Hash Manually

### Option A: Use the Utility Class

Run this command:
```bash
./gradlew run --args="com.curamatrix.hsm.util.PasswordEncoderUtil"
```

Or run the main method in `src/main/java/com/curamatrix/hsm/util/PasswordEncoderUtil.java`

This will output BCrypt hashes for common passwords.

### Option B: Use Online Tool

1. Go to: https://bcrypt-generator.com/
2. Enter your password
3. Use rounds: 10
4. Copy the generated hash

### Option C: Insert Directly into Database

```sql
-- Insert user
INSERT INTO users (email, password, full_name, phone, is_active)
VALUES (
    'dr-prathmesh@curamatrix.com',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',  -- BCrypt hash for "doctor123"
    'Dr. Prathmesh Sharma',
    '9876543212',
    TRUE
);

-- Get the user ID
SET @user_id = LAST_INSERT_ID();

-- Assign ROLE_DOCTOR
INSERT INTO user_roles (user_id, role_id)
VALUES (@user_id, (SELECT id FROM roles WHERE name = 'ROLE_DOCTOR'));

-- Create doctor profile
INSERT INTO doctors (user_id, department_id, specialization, license_number, qualification, experience_years, consultation_fee)
VALUES (
    @user_id,
    1,  -- General Medicine department
    'General Medicine',
    'MCI-GM-002',
    'MBBS, MD',
    8,
    600.00
);
```

---

## Common BCrypt Hashes (for testing)

| Password | BCrypt Hash |
|----------|-------------|
| admin123 | `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` |
| doctor123 | `$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG` |
| reception123 | `$2a$10$EkRAGTGYSg7HnP3UQ0aBxO7yQOEKagDnqJXuFP3Tl1E.Vx/vK5jK6` |
| password123 | `$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi` |

---

## Verify User Creation

### Check in Database
```sql
SELECT u.id, u.email, u.full_name, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE u.email = 'dr-prathmesh@curamatrix.com';
```

### Test Login via API
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "dr-prathmesh@curamatrix.com",
  "password": "doctor123"
}
```

If successful, you'll receive a JWT token.

---

## Troubleshooting

### Error: "Email already exists"
The user is already in the database. Try logging in or use a different email.

### Error: "Department not found"
Check available departments:
```bash
GET http://localhost:8080/api/departments
Authorization: Bearer YOUR_JWT_TOKEN
```

### Error: "Role not found"
Make sure the roles table has data. Run the seed script:
```sql
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_DOCTOR'),
    ('ROLE_RECEPTIONIST');
```

---

## Quick Test Script

Save this as `test-login.sh`:

```bash
#!/bin/bash

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "doctor@curamatrix.com",
    "password": "doctor123"
  }' | jq
```

Run: `chmod +x test-login.sh && ./test-login.sh`
