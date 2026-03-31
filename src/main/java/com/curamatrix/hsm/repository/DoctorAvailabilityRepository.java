package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    Optional<DoctorAvailability> findByDoctorIdAndAvailabilityDateAndTenantId(
            Long doctorId, LocalDate date, Long tenantId);

    List<DoctorAvailability> findByAvailabilityDateAndTenantId(LocalDate date, Long tenantId);
}
