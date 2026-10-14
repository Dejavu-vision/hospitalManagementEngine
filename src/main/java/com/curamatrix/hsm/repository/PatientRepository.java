package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    
    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "p.phone LIKE CONCAT('%', CAST(:search AS string), '%')")
    Page<Patient> searchPatients(@Param("search") String search, Pageable pageable);
    
    long countByTenantId(Long tenantId);

    Page<Patient> findByTenantId(Long tenantId, Pageable pageable);

    boolean existsByPatientCode(String patientCode);

    java.util.Optional<Patient> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirthAndTenantId(
            String firstName, String lastName, java.time.LocalDate dateOfBirth, Long tenantId);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND (" +
           "LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "p.phone LIKE CONCAT('%', CAST(:q AS string), '%') OR " +
           "LOWER(p.patientCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
    Page<Patient> searchByTenant(@Param("q") String query,
                                  @Param("tenantId") Long tenantId,
                                  Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId " +
           "AND (CAST(:q AS string) IS NULL OR " +
           "     LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "     p.phone LIKE CONCAT('%', CAST(:q AS string), '%') OR " +
           "     LOWER(p.patientCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))) " +
           "AND (CAST(:gender AS string) IS NULL OR p.gender = :gender) " +
           "AND (CAST(:bloodGroup AS string) IS NULL OR p.bloodGroup = :bloodGroup)")
    Page<Patient> searchWithFilters(@Param("q") String query,
                                     @Param("gender") String gender,
                                     @Param("bloodGroup") String bloodGroup,
                                     @Param("tenantId") Long tenantId,
                                     Pageable pageable);
    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.gender = :gender " +
           "AND (p.dateOfBirth = :dob OR YEAR(p.dateOfBirth) = YEAR(:dob)) " +
           "ORDER BY p.id DESC")
    java.util.List<Patient> findDuplicates(
            @Param("gender") com.curamatrix.hsm.enums.Gender gender,
            @Param("dob") java.time.LocalDate dob,
            @Param("tenantId") Long tenantId);

    /**
     * Find patients by exact phone number within a tenant.
     * Used by checkDuplicate — phone is the only field checked for duplicates.
     */
    @Query("SELECT p FROM Patient p WHERE p.phone = :phone AND p.tenantId = :tenantId ORDER BY p.id DESC")
    java.util.List<Patient> findByPhoneAndTenantId(
            @Param("phone") String phone,
            @Param("tenantId") Long tenantId);

    /**
     * Search patients scoped to a specific set of IDs (used when date-range filter is active).
     * The patientIds list comes from AppointmentRepository.findDistinctPatientIdsByVisitDateRange.
     */
    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId " +
           "AND p.id IN :patientIds " +
           "AND (CAST(:q AS string) IS NULL OR " +
           "     LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
           "     p.phone LIKE CONCAT('%', CAST(:q AS string), '%') OR " +
           "     LOWER(p.patientCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))) " +
           "AND (CAST(:gender AS string) IS NULL OR p.gender = :gender) " +
           "AND (CAST(:bloodGroup AS string) IS NULL OR p.bloodGroup = :bloodGroup)")
    Page<Patient> searchWithVisitFilter(@Param("q") String query,
                                         @Param("gender") String gender,
                                         @Param("bloodGroup") String bloodGroup,
                                         @Param("tenantId") Long tenantId,
                                         @Param("patientIds") java.util.List<Long> patientIds,
                                         Pageable pageable);
}
