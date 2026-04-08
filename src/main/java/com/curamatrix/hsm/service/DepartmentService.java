package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.DoctorWithAvailabilityResponse;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
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
