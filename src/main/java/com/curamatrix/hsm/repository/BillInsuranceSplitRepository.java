package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.BillInsuranceSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillInsuranceSplitRepository extends JpaRepository<BillInsuranceSplit, Long> {
    Optional<BillInsuranceSplit> findByBillingId(Long billingId);
}
