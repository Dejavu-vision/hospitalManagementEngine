package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.DoctorAvailabilityRequest;
import com.curamatrix.hsm.dto.response.DoctorAvailabilityResponse;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DoctorAvailabilityService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;

    public DoctorAvailabilityResponse getAvailability(Long doctorId, LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        Optional<DoctorAvailability> record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, date, tenantId);
        boolean isPresent = record.map(DoctorAvailability::getIsPresent).orElse(false);
        return DoctorAvailabilityResponse.builder()
                .doctorId(doctorId).date(date).isPresent(isPresent).build();
    }

    public DoctorAvailabilityResponse upsertAvailability(Long doctorId,
                                                          DoctorAvailabilityRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        DoctorAvailability record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, request.getDate(), tenantId)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor).availabilityDate(request.getDate()).build());
        record.setIsPresent(request.getIsPresent());
        record = availabilityRepository.save(record);
        return DoctorAvailabilityResponse.builder()
                .doctorId(doctorId).date(record.getAvailabilityDate())
                .isPresent(record.getIsPresent()).build();
    }
}
