package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Diagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    Optional<Diagnosis> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    List<Diagnosis> findByAppointmentPatientIdOrderByCreatedAtDesc(Long patientId);
}
