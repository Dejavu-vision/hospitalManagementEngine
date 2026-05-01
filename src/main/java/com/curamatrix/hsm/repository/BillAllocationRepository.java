package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.BillAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillAllocationRepository extends JpaRepository<BillAllocation, Long> {
    List<BillAllocation> findAllByBillingIdAndTenantId(Long billingId, Long tenantId);
    List<BillAllocation> findAllByPaymentIdAndTenantId(Long paymentId, Long tenantId);
}
