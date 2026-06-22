package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.BillingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingItemRepository extends JpaRepository<BillingItem, Long> {
}
