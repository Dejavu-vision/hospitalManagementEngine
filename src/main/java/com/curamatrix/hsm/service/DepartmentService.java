package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.DepartmentRequest;
import com.curamatrix.hsm.dto.response.DoctorWithAvailabilityResponse;
import com.curamatrix.hsm.entity.Department;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.DepartmentRepository;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import com.curamatrix.hsm.entity.HospitalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DepartmentRepository departmentRepository;
    private final HospitalServiceRepository hospitalServiceRepository;

    // ─── Department CRUD ─────────────────────────────────────────────────────

    public List<Department> getAllDepartments() {
        Long tenantId = TenantContext.getTenantId();
        return departmentRepository.findByTenantIdAndIsActiveTrue(tenantId);
    }

    public Department getDepartmentById(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return departmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }

    @Transactional
    public Department createDepartment(DepartmentRequest request) {
        Long tenantId = TenantContext.getTenantId();

        departmentRepository.findByNameAndTenantId(request.getName(), tenantId)
                .ifPresent(d -> {
                    throw new DuplicateResourceException("Department", "name", request.getName());
                });

        Department department = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        department.setTenantId(tenantId);

        department = departmentRepository.save(department);
        log.info("Created department: {} for tenant: {}", department.getName(), tenantId);
        return department;
    }

    @Transactional
    public Department updateDepartment(Long id, DepartmentRequest request) {
        Long tenantId = TenantContext.getTenantId();

        Department department = departmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));

        // Check for duplicate name if changed
        if (!department.getName().equals(request.getName())) {
            departmentRepository.findByNameAndTenantId(request.getName(), tenantId)
                    .ifPresent(d -> {
                        throw new DuplicateResourceException("Department", "name", request.getName());
                    });
        }

        department.setName(request.getName());
        department.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            department.setIsActive(request.getIsActive());
        }

        department = departmentRepository.save(department);
        log.info("Updated department: {} for tenant: {}", department.getName(), tenantId);
        return department;
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Department department = departmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        
        // Soft delete the department
        department.setIsActive(false);
        departmentRepository.save(department);

        // Deactivate all services belonging to this department
        List<HospitalService> services = hospitalServiceRepository.findAllByTenantIdAndDepartmentId(tenantId, id);
        for (HospitalService service : services) {
            service.setActive(false);
        }
        hospitalServiceRepository.saveAll(services);

        log.info("Soft deleted department: {} and deactivated its {} services for tenant: {}", id, services.size(), tenantId);
    }

    // ─── Doctor Availability ─────────────────────────────────────────────────

    public List<DoctorWithAvailabilityResponse> getDoctorsWithAvailability(Long departmentId) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        List<Doctor> doctors = doctorRepository.findByDepartmentIdAndTenantId(departmentId, tenantId);
        return doctors.stream().map(d -> {
            // Default to true (present) when no record exists
            boolean present = availabilityRepository
                    .findByDoctorIdAndAvailabilityDateAndTenantId(d.getId(), today, tenantId)
                    .map(DoctorAvailability::getIsPresent).orElse(true);
            return DoctorWithAvailabilityResponse.builder()
                    .doctorId(d.getId())
                    .doctorName(d.getUser().getFullName())
                    .qualification(d.getQualification())
                    .consultationFee(d.getConsultationFee())
                    .isPresentToday(present)
                    .build();
        }).collect(Collectors.toList());
    }
}
