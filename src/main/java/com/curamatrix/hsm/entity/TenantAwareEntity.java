package com.curamatrix.hsm.entity;

import com.curamatrix.hsm.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity for all tenant-aware entities.
 * Automatically sets tenant_id on persist/update.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantAwareEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    @PreUpdate
    public void setTenantIdFromContext() {
        if (this.tenantId == null) {
            Long contextTenantId = TenantContext.getTenantId();
            if (contextTenantId == null) {
                throw new IllegalStateException("Tenant context not set. Cannot persist entity without tenant.");
            }
            this.tenantId = contextTenantId;
        }
    }
}
