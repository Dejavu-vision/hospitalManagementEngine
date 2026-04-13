package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.DoctorAvailabilityRequest;
import com.curamatrix.hsm.dto.request.DoctorStatusUpdateRequest;
import com.curamatrix.hsm.dto.response.DoctorAvailabilityResponse;
import com.curamatrix.hsm.entity.Doctor;
import com.curamatrix.hsm.entity.DoctorAvailability;
import com.curamatrix.hsm.enums.DoctorStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.DoctorAvailabilityRepository;
import com.curamatrix.hsm.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DoctorAvailabilityService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;

    /** Get today's availability for a single doctor */
    public DoctorAvailabilityResponse getAvailability(Long doctorId, LocalDate date) {
        Long tenantId = TenantContext.getTenantId();
        Optional<DoctorAvailability> record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, date, tenantId);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        // Default: present and ON_DUTY when no record exists
        return record.map(r -> toResponse(r, doctor))
                .orElseGet(() -> DoctorAvailabilityResponse.builder()
                        .userId(doctor.getUser().getId())
                        .doctorId(doctorId)
                        .doctorName(doctor.getUser().getFullName())
                        .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                        .date(date)
                        .isPresent(true)
                        .status(DoctorStatus.ON_DUTY)
                        .build());
    }

    /** Get today's availability for all doctors in a tenant */
    public List<DoctorAvailabilityResponse> getTodayAvailability() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        List<Doctor> allDoctors = doctorRepository.findByTenantId(tenantId);
        return allDoctors.stream().map(doctor -> {
            Optional<DoctorAvailability> record = availabilityRepository
                    .findByDoctorIdAndAvailabilityDateAndTenantId(doctor.getId(), today, tenantId);
            return record.map(r -> toResponse(r, doctor))
                    .orElseGet(() -> DoctorAvailabilityResponse.builder()
                            .userId(doctor.getUser().getId())
                            .doctorId(doctor.getId())
                            .doctorName(doctor.getUser().getFullName())
                            .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                            .date(today)
                            .isPresent(true)
                            .status(DoctorStatus.ON_DUTY)
                            .build());
        }).collect(Collectors.toList());
    }

    /** Upsert presence + duty hours (used by admin for scheduling) */
    public DoctorAvailabilityResponse upsertAvailability(Long doctorId, DoctorAvailabilityRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        DoctorAvailability record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, request.getDate(), tenantId)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor).availabilityDate(request.getDate()).build());
        record.setIsPresent(request.getIsPresent());
        if (!request.getIsPresent()) {
            record.setStatus(DoctorStatus.OFF_DUTY);
        } else if (record.getStatus() == DoctorStatus.OFF_DUTY) {
            record.setStatus(DoctorStatus.ON_DUTY);
        }
        record = availabilityRepository.save(record);
        return toResponse(record, doctor);
    }

    /** Update real-time status (used by receptionist during the day) */
    public DoctorAvailabilityResponse updateStatus(Long doctorId, DoctorStatusUpdateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        DoctorAvailability record = availabilityRepository
                .findByDoctorIdAndAvailabilityDateAndTenantId(doctorId, today, tenantId)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor).availabilityDate(today).isPresent(true).build());

        record.setStatus(request.getStatus());
        record.setStatusNote(request.getStatusNote());
        record.setAvailableFrom(request.getAvailableFrom());
        if (request.getDutyStart() != null) record.setDutyStart(request.getDutyStart());
        if (request.getDutyEnd() != null) record.setDutyEnd(request.getDutyEnd());

        // Sync isPresent with status
        record.setIsPresent(request.getStatus() != DoctorStatus.OFF_DUTY);

        record = availabilityRepository.save(record);
        log.info("Doctor {} status updated to {} by receptionist", doctorId, request.getStatus());
        return toResponse(record, doctor);
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailability r, Doctor doctor) {
        return DoctorAvailabilityResponse.builder()
                .userId(doctor.getUser().getId())
                .doctorId(r.getDoctor().getId())
                .doctorName(doctor.getUser().getFullName())
                .departmentName(doctor.getDepartment() != null ? doctor.getDepartment().getName() : null)
                .date(r.getAvailabilityDate())
                .isPresent(r.getIsPresent())
                .status(r.getStatus())
                .statusNote(r.getStatusNote())
                .availableFrom(r.getAvailableFrom())
                .dutyStart(r.getDutyStart())
                .dutyEnd(r.getDutyEnd())
                .build();
    }
}
