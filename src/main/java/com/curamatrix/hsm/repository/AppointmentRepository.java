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

    // All appointments for a tenant on a given date (for queue dashboard)
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate = :date AND a.tenantId = :tenantId " +
           "ORDER BY a.doctor.id, a.tokenNumber, a.appointmentTime")
    List<Appointment> findAllByDateAndTenant(@Param("date") LocalDate date,
                                              @Param("tenantId") Long tenantId);

    // Count by status for dashboard stats
    @Query("SELECT a.status, COUNT(a) FROM Appointment a " +
           "WHERE a.appointmentDate = :date AND a.tenantId = :tenantId GROUP BY a.status")
    List<Object[]> countByStatusForDate(@Param("date") LocalDate date,
                                         @Param("tenantId") Long tenantId);

    // Patients in CHECKED_IN status with their check-in time (for wait-time alerts)
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate = :date AND a.tenantId = :tenantId " +
           "AND a.status = 'CHECKED_IN' ORDER BY a.checkedInAt ASC")
    List<Appointment> findCheckedInByDateAndTenant(@Param("date") LocalDate date,
                                                    @Param("tenantId") Long tenantId);

    // Tenant-scoped patient visit history
    @Query("SELECT COUNT(a), MAX(a.appointmentDate) FROM Appointment a " +
           "WHERE a.patient.id = :patientId AND a.tenantId = :tenantId " +
           "AND a.status = 'COMPLETED'")
    List<Object[]> findVisitSummaryByPatient(@Param("patientId") Long patientId,
                                              @Param("tenantId") Long tenantId);

    // Tenant-scoped today queue per doctor
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.appointmentDate = :date AND a.tenantId = :tenantId " +
           "AND a.status NOT IN ('CANCELLED', 'NO_SHOW') " +
           "ORDER BY CASE WHEN a.type = 'WALK_IN' THEN a.tokenNumber ELSE 999999 END, a.appointmentTime")
    List<Appointment> findTodayQueueByDoctorAndTenant(@Param("doctorId") Long doctorId,
                                                       @Param("date") LocalDate date,
                                                       @Param("tenantId") Long tenantId);

    // Doctor queue lengths for auto-assignment
    @Query("SELECT a.doctor.id, COUNT(a) FROM Appointment a " +
           "WHERE a.doctor.department.id = :deptId AND a.appointmentDate = :date " +
           "AND a.tenantId = :tenantId AND a.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS') " +
           "GROUP BY a.doctor.id ORDER BY COUNT(a) ASC")
    List<Object[]> findQueueLengthsByDepartment(@Param("deptId") Long deptId,
                                                 @Param("date") LocalDate date,
                                                 @Param("tenantId") Long tenantId);

    // Tenant-wide queue lengths for all doctors (for composite booking context API)
    @Query("SELECT a.doctor.id, COUNT(a) FROM Appointment a " +
           "WHERE a.appointmentDate = :date AND a.tenantId = :tenantId " +
           "AND a.status IN ('BOOKED', 'CHECKED_IN', 'IN_PROGRESS') " +
           "GROUP BY a.doctor.id")
    List<Object[]> findQueueLengthsByTenant(@Param("date") LocalDate date,
                                            @Param("tenantId") Long tenantId);

    // Count remaining IN_PROGRESS appointments for a doctor excluding a given appointment
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.appointmentDate = :date AND a.tenantId = :tenantId " +
           "AND a.status = 'IN_PROGRESS' AND a.id != :excludeId")
    Long countOtherInProgressByDoctor(@Param("doctorId") Long doctorId,
                                       @Param("date") LocalDate date,
                                       @Param("tenantId") Long tenantId,
                                       @Param("excludeId") Long excludeId);

    // Distinct patients who have had appointments with a specific doctor (tenant-scoped)
    @Query("SELECT DISTINCT a.patient FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.tenantId = :tenantId ORDER BY a.patient.firstName, a.patient.lastName")
    List<com.curamatrix.hsm.entity.Patient> findDistinctPatientsByDoctor(
            @Param("doctorId") Long doctorId,
            @Param("tenantId") Long tenantId);
}
