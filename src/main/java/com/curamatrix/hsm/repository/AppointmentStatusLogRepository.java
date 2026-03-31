package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.AppointmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppointmentStatusLogRepository extends JpaRepository<AppointmentStatusLog, Long> {

    List<AppointmentStatusLog> findByAppointmentIdOrderByChangedAtAsc(Long appointmentId);

    @Query("SELECT COUNT(l) FROM AppointmentStatusLog l WHERE l.appointment.id = :apptId")
    long countByAppointmentId(@Param("apptId") Long apptId);
}
