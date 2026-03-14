# Complete SaaS Schema - Quick Reference

## 📄 File: `COMPLETE_SAAS_SCHEMA.sql`

**One SQL file to rule them all!** Complete multi-tenant SaaS database schema with seed data.

---

## 🚀 Quick Start

### Execute the Script
```bash
mysql -u root -p < docs/COMPLETE_SAAS_SCHEMA.sql
```

**That's it!** Everything is set up.

---

## 📊 What Gets Created

### 20 Tables

#### Multi-Tenant Core (1 table)
1. **tenants** - Hospital/tenant management

#### User Management (3 tables)
2. **roles** - User roles (SUPER_ADMIN, ADMIN, DOCTOR, RECEPTIONIST)
3. **users** - All users (tenant-aware)
4. **user_roles** - User-role mapping

#### Hospital Structure (3 tables)
5. **departments** - Hospital departments (tenant-aware)
6. **doctors** - Doctor profiles (tenant-aware)
7. **receptionists** - Receptionist profiles (tenant-aware)

#### Patient & Appointments (2 tables)
8. **patients** - Patient records (tenant-aware)
9. **appointments** - Appointments & queue (tenant-aware)

#### Clinical (3 tables)
10. **diagnoses** - Patient diagnoses (tenant-aware)
11. **medicines** - Medicine master data (shared)
12. **prescriptions** - Prescribed medicines (tenant-aware)
13. **medicine_inventory** - Stock management

#### Billing (2 tables)
14. **billings** - Invoices (tenant-aware)
15. **billing_items** - Invoice line items (tenant-aware)

#### SaaS Management (3 tables)
16. **tenant_subscriptions** - Subscription history
17. **tenant_usage_stats** - Usage analytics
18. **tenant_audit_log** - Audit trail

---

## 🔑 Default Credentials

### Super Admin (Platform Level)
```
Email: superadmin@curamatrix.com
Password: admin123
Tenant: default-hospital
Access: All tenants
```

### Default Hospital Admin
```
Email: admin@curamatrix.com
Password: admin123
Tenant: default-hospital
Access: Default hospital only
```

### Default Hospital Doctor
```
Email: doctor@curamatrix.com
Password: doctor123
Tenant: default-hospital
Role: ROLE_DOCTOR
```

### Default Hospital Receptionist
```
Email: reception@curamatrix.com
Password: reception123
Tenant: default-hospital
Role: ROLE_RECEPTIONIST
```

---

## 📦 Seed Data Included

### 1 Default Tenant
- **tenant_key**: `default-hospital`
- **plan**: PREMIUM (unlimited)
- **status**: Active

### 4 Roles
- ROLE_SUPER_ADMIN
- ROLE_ADMIN
- ROLE_DOCTOR
- ROLE_RECEPTIONIST

### 4 Users
- 1 Super Admin
- 1 Hospital Admin
- 1 Doctor
- 1 Receptionist

### 10 Departments
- General Medicine
- Cardiology
- Orthopedics
- Pediatrics
- Dermatology
- ENT
- Ophthalmology
- Gynecology
- Neurology
- Dentistry

### 25 Medicines
- Paracetamol variants
- Antibiotics (Amoxicillin, Azithromycin, Ciprofloxacin)
- Diabetes medicines (Metformin, Glimepiride)
- Blood pressure medicines (Amlodipine, Atenolol)
- Pain relievers (Ibuprofen, Diclofenac)
- And more...

### 27 Inventory Records
- Stock for all medicines
- Includes low stock test data
- Includes expiring soon test data

---

## 🔍 Verification Queries

After running the script, verify with:

### Check All Tables
```sql
SELECT TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'hospitalsystems'
ORDER BY TABLE_NAME;
```
**Expected: 20 tables**

### Check Tenants
```sql
SELECT * FROM tenants;
```
**Expected: 1 tenant (default-hospital)**

### Check Users
```sql
SELECT u.email, u.full_name, r.name AS role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id;
```
**Expected: 4 users**

### Check Departments
```sql
SELECT COUNT(*) FROM departments;
```
**Expected: 10 departments**

### Check Medicines
```sql
SELECT COUNT(*) FROM medicines;
```
**Expected: 25 medicines**

### Check Inventory
```sql
SELECT COUNT(*) FROM medicine_inventory;
```
**Expected: 27 inventory records**

---

## 🏗️ Schema Structure

### Tenant Isolation
Every table (except `medicines` and `medicine_inventory`) has:
- `tenant_id` column
- Foreign key to `tenants` table
- Composite indexes with `tenant_id`

### Example: Users Table
```sql
CREATE TABLE users (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   BIGINT NOT NULL,  -- ✅ Tenant isolation
    email       VARCHAR(255) NOT NULL,
    ...
    UNIQUE KEY uk_users_email_tenant (email, tenant_id),  -- ✅ Per-tenant unique
    INDEX idx_users_tenant (tenant_id),  -- ✅ Performance
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
```

---

## 🎯 Key Features

### 1. Complete Data Isolation
- All queries filtered by `tenant_id`
- Cross-tenant access impossible
- Automatic tenant assignment

### 2. Subscription Management
- 3 plans: BASIC, STANDARD, PREMIUM
- Quota enforcement ready
- Expiry tracking

### 3. Performance Optimized
- Composite indexes on (tenant_id, ...)
- Proper foreign keys
- Efficient query plans

### 4. Audit Ready
- `tenant_audit_log` table
- Tracks all operations
- IP and user agent logging

### 5. Usage Tracking
- `tenant_usage_stats` table
- Daily statistics
- Billing ready

---

## 🔄 Migration from Single-Tenant

If you already have data, use `MULTI_TENANT_MIGRATION.sql` instead:
```bash
mysql -u root -p < docs/MULTI_TENANT_MIGRATION.sql
```

This will:
1. Add `tenant_id` to existing tables
2. Create default tenant
3. Assign existing data to default tenant
4. Add new SaaS tables

---

## 📈 Next Steps

### 1. Test Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "superadmin@curamatrix.com",
    "password": "admin123",
    "tenantKey": "default-hospital"
  }'
```

### 2. Register New Hospital
```bash
curl -X POST http://localhost:8080/api/super-admin/tenants \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantKey": "apollo-mumbai",
    "hospitalName": "Apollo Hospital Mumbai",
    "subscriptionPlan": "PREMIUM",
    ...
  }'
```

### 3. Test Multi-Tenancy
- Login to different hospitals
- Create data in each
- Verify isolation

---

## 🐛 Troubleshooting

### "Database already exists"
```sql
DROP DATABASE hospitalsystems;
-- Then run the script again
```

### "Access denied"
```bash
# Check MySQL credentials
mysql -u root -p
# Update password in application.yml
```

### "Foreign key constraint fails"
```sql
-- Check if tables exist
SHOW TABLES;
-- Check foreign key relationships
SELECT * FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'hospitalsystems';
```

---

## 📊 Database Size

**Approximate sizes:**
- Empty schema: ~2 MB
- With seed data: ~3 MB
- Per tenant (1000 patients): ~50 MB
- 100 tenants: ~5 GB

---

## 🔒 Security Notes

1. **Change default passwords** in production
2. **Use strong JWT secret** in application.yml
3. **Enable SSL** for MySQL connections
4. **Regular backups** per tenant
5. **Monitor audit logs** for suspicious activity

---

## 📞 Support

- **Full Documentation**: `SAAS_ARCHITECTURE.md`
- **API Testing**: `postman/SAAS_POSTMAN_REQUESTS.md`
- **Migration Guide**: `MULTI_TENANT_MIGRATION.sql`
- **Complete Guide**: `SAAS_COMPLETE_GUIDE.md`

---

**One file. Complete SaaS database. Ready to scale! 🚀**
