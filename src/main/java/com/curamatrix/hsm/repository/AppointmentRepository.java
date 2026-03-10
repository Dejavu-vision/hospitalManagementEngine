package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Appointment;
import com.curamatrix.hsm.enums.AppointmentStatus;
import com.curamatrix.hsm.enums.AppointmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    Page<Appointment> findByAppointmentDate(LocalDate date, Pageable pageable);
    
    Page<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate date, Pageable pageable);
    
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date " +
           "AND a.appointmentTime = :time AND a.status != 'CANCELLED'")
    Optional<Appointment> findByDoctorAndDateAndTime(@Param("doctorId") Long doctorId, 
                                                      @Param("date") LocalDate date, 
                                                      @Param("time") LocalTime time);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date " +
           "ORDER BY CASE WHEN a.type = 'WALK_IN' THEN a.tokenNumber ELSE 999999 END, a.appointmentTime")
    List<Appointment> findTodayQueueByDoctor(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
    
    @Query("SELECT MAX(a.tokenNumber) FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.appointmentDate = :date AND a.type = 'WALK_IN'")
    Integer findMaxTokenNumber(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
    
    @Query("SELECT a FROM Appointment a WHERE " +
           "(:date IS NULL OR a.appointmentDate = :date) AND " +
           "(:doctorId IS NULL OR a.doctor.id = :doctorId) AND " +
           "(:patientId IS NULL OR a.patient.id = :patientId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:type IS NULL OR a.type = :type)")
    Page<Appointment> findByFilters(@Param("date") LocalDate date,
                                    @Param("doctorId") Long doctorId,
                                    @Param("patientId") Long patientId,
                                    @Param("status") AppointmentStatus status,
                                    @Param("type") AppointmentType type,
                                    Pageable pageable);
}
