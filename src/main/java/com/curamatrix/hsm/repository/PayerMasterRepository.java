package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PayerMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayerMasterRepository extends JpaRepository<PayerMaster, Long> {
    List<PayerMaster> findAllByOrderByInsurerNameAsc();
}
