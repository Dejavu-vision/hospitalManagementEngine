# CuraMatrix HSM - Multi-Tenant SaaS Architecture

## Overview

Transform the Hospital Management System into a multi-tenant SaaS platform where multiple hospitals can use the same application with complete data isolation.

---

## 🏗️ Multi-Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT TIER (Frontend)                   │
│  React/Angular/Vue → API Gateway → Load Balancer            │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                   APPLICATION TIER (Backend)                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Spring Boot Instances (Horizontal Scaling)          │  │
│  │  - Tenant Context Filter                             │  │
│  │  - JWT Authentication                                 │  │
│  │  - Business Logic                                     │  │
│  │  - Multi-tenant Data Access                          │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      DATA TIER (Database)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Shared Database with Tenant Isolation               │  │
│  │  - All tables have tenant_id column                  │  │
│  │  - Row-level security                                 │  │
│  │  - Tenant-specific indexes                           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 Multi-Tenancy Strategy

### Approach: Shared Database, Shared Schema (Discriminator Column)

**Why this approach?**
- ✅ Cost-effective (single database)
- ✅ Easy maintenance and updates
- ✅ Efficient resource utilization
- ✅ Simple backup and disaster recovery
- ✅ Scalable for 100+ hospitals

**Data Isolation:**
- Every table has `tenant_id` column
- All queries automatically filtered by tenant
- Tenant context set from JWT token
- No cross-tenant data access possible

---

## 🏥 Tenant Model

### Hospital (Tenant) Entity
```java
@Entity
@Table(name = "tenants")
public class Tenant {
    private Long id;
    private String tenantKey;        // Unique identifier (e.g., "apollo-mumbai")
    private String hospitalName;     // Display name
    private String subscriptionPlan; // BASIC, STANDARD, PREMIUM
    private LocalDate subscriptionStart;
    private LocalDate subscriptionEnd;
    private Boolean isActive;
    private Integer maxUsers;
    private Integer maxPatients;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String logo;             // URL to hospital logo
    private Map<String, Object> settings; // JSON configuration
}
```

### Tenant Context
```java
public class TenantContext {
    private static ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static Long getTenantId() {
        return currentTenant.get();
    }
    
    public static void clear() {
        currentTenant.remove();
    }
}
```

---

## 🔐 Authentication Flow

### 1. Super Admin (Platform Level)
```
Role: ROLE_SUPER_ADMIN
Access: All tenants, tenant management
Login: superadmin@curamatrix.com
```

### 2. Hospital Admin (Tenant Level)
```
Role: ROLE_ADMIN
Access: Single tenant, user management within tenant
Login: admin@apollo-mumbai.curamatrix.com
Tenant: apollo-mumbai
```

### 3. Hospital Staff (Tenant Level)
```
Role: ROLE_DOCTOR, ROLE_RECEPTIONIST
Access: Single tenant, operational tasks
Login: doctor@apollo-mumbai.curamatrix.com
Tenant: apollo-mumbai
```

### JWT Token Structure
```json
{
  "sub": "doctor@apollo-mumbai.curamatrix.com",
  "userId": 123,
  "tenantId": 5,
  "tenantKey": "apollo-mumbai",
  "role": "ROLE_DOCTOR",
  "hospitalName": "Apollo Hospital Mumbai",
  "exp": 1234567890
}
```

---

## 📊 Database Schema Changes

### All Existing Tables Get tenant_id
```sql
-- Example: patients table
ALTER TABLE patients ADD COLUMN tenant_id BIGINT NOT NULL;
ALTER TABLE patients ADD CONSTRAINT fk_patients_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenants(id);
CREATE INDEX idx_patients_tenant ON patients(tenant_id);

-- Composite unique constraints now include tenant_id
ALTER TABLE users DROP CONSTRAINT uk_users_email;
ALTER TABLE users ADD CONSTRAINT uk_users_email_tenant 
    UNIQUE (email, tenant_id);
```

### New Tables

#### tenants
```sql
CREATE TABLE tenants (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_key          VARCHAR(100) UNIQUE NOT NULL,
    hospital_name       VARCHAR(255) NOT NULL,
    subscription_plan   VARCHAR(50) NOT NULL,
    subscription_start  DATE NOT NULL,
    subscription_end    DATE NOT NULL,
    is_active           BOOLEAN DEFAULT TRUE,
    max_users           INT DEFAULT 50,
    max_patients        INT DEFAULT 10000,
    contact_email       VARCHAR(255) NOT NULL,
    contact_phone       VARCHAR(20),
    address             TEXT,
    logo                VARCHAR(500),
    settings            JSON,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### tenant_subscriptions (for billing)
```sql
CREATE TABLE tenant_subscriptions (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id       BIGINT NOT NULL,
    plan_name       VARCHAR(50) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    billing_cycle   VARCHAR(20) NOT NULL, -- MONTHLY, YEARLY
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL, -- ACTIVE, EXPIRED, CANCELLED
    payment_method  VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
```

#### tenant_usage_stats (for analytics)
```sql
CREATE TABLE tenant_usage_stats (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id           BIGINT NOT NULL,
    stat_date           DATE NOT NULL,
    total_users         INT DEFAULT 0,
    total_patients      INT DEFAULT 0,
    total_appointments  INT DEFAULT 0,
    total_diagnoses     INT DEFAULT 0,
    api_calls           INT DEFAULT 0,
    storage_used_mb     INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    UNIQUE KEY uk_tenant_stat_date (tenant_id, stat_date)
);
```

---

## 🛡️ Tenant Isolation Implementation

### 1. Tenant Interceptor
```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String token = extractToken(request);
        if (token != null) {
            Long tenantId = extractTenantIdFromToken(token);
            TenantContext.setTenantId(tenantId);
        }
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        TenantContext.clear();
    }
}
```

### 2. Base Entity with Tenant
```java
@MappedSuperclass
public abstract class TenantAwareEntity {
    
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    
    @PrePersist
    public void prePersist() {
        if (tenantId == null) {
            tenantId = TenantContext.getTenantId();
        }
    }
}
```

### 3. Repository with Tenant Filter
```java
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {
    
    @Query("SELECT e FROM #{#entityName} e WHERE e.tenantId = :tenantId")
    List<T> findAllByTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.tenantId = :tenantId")
    Optional<T> findByIdAndTenant(@Param("id") ID id, @Param("tenantId") Long tenantId);
}
```

### 4. Aspect for Automatic Tenant Filtering
```java
@Aspect
@Component
public class TenantFilterAspect {
    
    @Around("execution(* com.curamatrix.hsm.repository.*.*(..))")
    public Object addTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new TenantNotFoundException("No tenant context found");
        }
        
        // Add tenant filter to query
        EntityManager em = entityManagerFactory.createEntityManager();
        em.unwrap(Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenantId);
        
        return joinPoint.proceed();
    }
}
```

---

## 🚀 Subscription Plans

### Plan Features

| Feature | BASIC | STANDARD | PREMIUM |
|---------|-------|----------|---------|
| Max Users | 10 | 50 | Unlimited |
| Max Patients | 1,000 | 10,000 | Unlimited |
| Departments | 5 | 20 | Unlimited |
| Storage | 5 GB | 50 GB | 500 GB |
| API Rate Limit | 1000/hour | 10000/hour | Unlimited |
| Support | Email | Email + Chat | 24/7 Phone |
| Custom Branding | ❌ | ✅ | ✅ |
| Advanced Reports | ❌ | ✅ | ✅ |
| Multi-location | ❌ | ❌ | ✅ |
| Price/Month | $99 | $499 | $1,999 |

---

## 📡 API Changes

### New Endpoints

#### Super Admin APIs
```
POST   /api/super-admin/tenants              - Create new hospital
GET    /api/super-admin/tenants              - List all hospitals
GET    /api/super-admin/tenants/{id}         - Get hospital details
PUT    /api/super-admin/tenants/{id}         - Update hospital
DELETE /api/super-admin/tenants/{id}         - Deactivate hospital
GET    /api/super-admin/tenants/{id}/stats   - Usage statistics
POST   /api/super-admin/tenants/{id}/suspend - Suspend hospital
POST   /api/super-admin/tenants/{id}/activate - Activate hospital
```

#### Tenant Management APIs
```
GET    /api/tenant/info                      - Current tenant info
PUT    /api/tenant/settings                  - Update tenant settings
GET    /api/tenant/usage                     - Current usage stats
GET    /api/tenant/subscription              - Subscription details
POST   /api/tenant/upgrade                   - Upgrade plan
```

#### Modified Login
```
POST   /api/auth/login
Request:
{
  "email": "doctor@apollo-mumbai.curamatrix.com",
  "password": "password123",
  "tenantKey": "apollo-mumbai"  // NEW: Required for tenant identification
}

Response:
{
  "token": "...",
  "userId": 123,
  "tenantId": 5,
  "tenantKey": "apollo-mumbai",
  "hospitalName": "Apollo Hospital Mumbai",
  "role": "ROLE_DOCTOR",
  "subscriptionPlan": "PREMIUM",
  "subscriptionExpiry": "2027-12-31"
}
```

---

## 🔒 Security Enhancements

### 1. Tenant Validation
```java
@Aspect
@Component
public class TenantSecurityAspect {
    
    @Before("@annotation(RequiresTenant)")
    public void validateTenant(JoinPoint joinPoint) {
        Long tenantId = TenantContext.getTenantId();
        
        // Check if tenant is active
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException());
        
        if (!tenant.getIsActive()) {
            throw new TenantSuspendedException("Hospital account is suspended");
        }
        
        // Check subscription expiry
        if (tenant.getSubscriptionEnd().isBefore(LocalDate.now())) {
            throw new SubscriptionExpiredException("Subscription has expired");
        }
    }
}
```

### 2. Rate Limiting per Tenant
```java
@Component
public class TenantRateLimiter {
    
    private final Map<Long, RateLimiter> limiters = new ConcurrentHashMap<>();
    
    public boolean allowRequest(Long tenantId, String plan) {
        RateLimiter limiter = limiters.computeIfAbsent(tenantId, 
            id -> createLimiter(plan));
        return limiter.tryAcquire();
    }
    
    private RateLimiter createLimiter(String plan) {
        return switch (plan) {
            case "BASIC" -> RateLimiter.create(1000.0 / 3600); // 1000/hour
            case "STANDARD" -> RateLimiter.create(10000.0 / 3600);
            case "PREMIUM" -> RateLimiter.create(Double.MAX_VALUE);
            default -> RateLimiter.create(100.0 / 3600);
        };
    }
}
```

### 3. Resource Quota Enforcement
```java
@Service
public class QuotaService {
    
    public void checkUserQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        long currentUsers = userRepository.countByTenantId(tenantId);
        
        if (currentUsers >= tenant.getMaxUsers()) {
            throw new QuotaExceededException(
                "User limit reached. Please upgrade your plan.");
        }
    }
    
    public void checkPatientQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        long currentPatients = patientRepository.countByTenantId(tenantId);
        
        if (currentPatients >= tenant.getMaxPatients()) {
            throw new QuotaExceededException(
                "Patient limit reached. Please upgrade your plan.");
        }
    }
}
```

---

## 📈 Scalability Features

### 1. Horizontal Scaling
```yaml
# docker-compose.yml
services:
  app1:
    image: curamatrix-hsm:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8080
  
  app2:
    image: curamatrix-hsm:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8080
  
  nginx:
    image: nginx:latest
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
```

### 2. Caching Strategy
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "tenants",
            "departments",
            "medicines"
        );
    }
}

@Service
public class TenantService {
    
    @Cacheable(value = "tenants", key = "#tenantId")
    public Tenant getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow();
    }
}
```

### 3. Database Connection Pooling
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 🎯 Migration Strategy

### Phase 1: Add Tenant Infrastructure (Week 1)
1. Create `tenants` table
2. Add `tenant_id` to all tables
3. Create default tenant for existing data
4. Update all entities to extend `TenantAwareEntity`

### Phase 2: Implement Tenant Context (Week 2)
1. Create `TenantContext` and `TenantInterceptor`
2. Update JWT to include `tenantId`
3. Modify login to require `tenantKey`
4. Update all repositories with tenant filtering

### Phase 3: Add Multi-Tenant Features (Week 3)
1. Create Super Admin APIs
2. Implement subscription management
3. Add quota enforcement
4. Implement rate limiting

### Phase 4: Testing & Deployment (Week 4)
1. Test with multiple tenants
2. Performance testing
3. Security audit
4. Production deployment

---

## 💰 Pricing Model

### Subscription Plans
```java
public enum SubscriptionPlan {
    BASIC(99, 10, 1000, 5),
    STANDARD(499, 50, 10000, 50),
    PREMIUM(1999, -1, -1, 500);
    
    private final int monthlyPrice;
    private final int maxUsers;
    private final int maxPatients;
    private final int storageGB;
}
```

### Billing Integration
- Stripe for payment processing
- Automatic subscription renewal
- Invoice generation
- Payment failure handling
- Upgrade/downgrade handling

---

## 📊 Monitoring & Analytics

### Tenant Metrics
- Active users per tenant
- API calls per tenant
- Storage usage per tenant
- Patient registrations per tenant
- Revenue per tenant

### Platform Metrics
- Total tenants
- Total revenue
- System resource usage
- Database performance
- API response times

---

## 🔄 Backup & Disaster Recovery

### Per-Tenant Backup
```sql
-- Backup single tenant data
mysqldump hospitalsystems \
  --where="tenant_id=5" \
  patients appointments diagnoses prescriptions \
  > apollo_mumbai_backup.sql
```

### Restore Single Tenant
```sql
-- Restore tenant data
mysql hospitalsystems < apollo_mumbai_backup.sql
```

---

## 🎓 Best Practices

1. **Always validate tenant context** before data access
2. **Use composite indexes** with tenant_id as first column
3. **Cache tenant configuration** to reduce DB queries
4. **Monitor quota usage** proactively
5. **Log all cross-tenant access attempts**
6. **Regular security audits** for data isolation
7. **Automated testing** for tenant isolation
8. **Clear tenant context** after each request

---

This architecture supports:
- ✅ 100+ hospitals on single instance
- ✅ Complete data isolation
- ✅ Flexible subscription plans
- ✅ Horizontal scalability
- ✅ Easy maintenance and updates
- ✅ Cost-effective operations
