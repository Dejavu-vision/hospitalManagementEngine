package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.response.*;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpdReceptionService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final WardRepository wardRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * Single-call context loader for the IPD Admission Wizard Step 2.
     * Returns doctors, available beds (grouped by ward → room), patient insurance policies,
     * and recent OPD appointments (for OPD_CONVERT flow).
     */
    @Transactional(readOnly = true)
    public IpdBookingContextResponse getAdmissionContext(Long patientId) {
        Long tenantId = TenantContext.getTenantId();

        // Validate patient belongs to this tenant
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        // 1. Doctors
        List<DoctorSummary> doctors = doctorRepository.findByTenantId(tenantId).stream()
                .map(this::mapDoctor)
                .collect(Collectors.toList());

        // 2. Wards → Rooms → Available Beds
        List<WardWithBedsResponse> wards = wardRepository.findByTenantId(tenantId).stream()
                .map(ward -> {
                    List<RoomWithBedsResponse> rooms = roomRepository
                            .findByWardIdAndTenantId(ward.getId(), tenantId).stream()
                            .map(room -> {
                                List<AvailableBedSummaryResponse> beds = bedRepository
                                        .findByRoomIdAndTenantIdAndStatus(room.getId(), tenantId, BedStatus.AVAILABLE)
                                        .stream()
                                        .map(bed -> AvailableBedSummaryResponse.builder()
                                                .bedId(bed.getId())
                                                .bedNumber(bed.getBedNumber())
                                                .status(bed.getStatus())
                                                .build())
                                        .collect(Collectors.toList());
                                return RoomWithBedsResponse.builder()
                                        .roomId(room.getId())
                                        .roomNumber(room.getRoomNumber())
                                        .roomType(room.getRoomType())
                                        .amenities(room.getAmenities())
                                        .beds(beds)
                                        .build();
                            })
                            .collect(Collectors.toList());
                    return WardWithBedsResponse.builder()
                            .wardId(ward.getId())
                            .wardName(ward.getName())
                            .floor(ward.getFloor())
                            .rooms(rooms)
                            .build();
                })
                .collect(Collectors.toList());

        // 3. Patient insurance policies
        List<PolicySummaryResponse> policies = insurancePolicyRepository
                .findByPatientIdAndTenantId(patientId, tenantId).stream()
                .map(policy -> PolicySummaryResponse.builder()
                        .policyId(policy.getId())
                        .payerId(policy.getPayer() != null ? policy.getPayer().getId() : null)
                        .payerName(policy.getPayer() != null ? policy.getPayer().getInsurerName() : null)
                        .tpaName(policy.getPayer() != null ? policy.getPayer().getTpaName() : null)
                        .policyNumber(policy.getPolicyNumber())
                        .policyType(policy.getPolicyType() != null ? policy.getPolicyType().name() : null)
                        .sumInsured(policy.getSumInsured())
                        .build())
                .collect(Collectors.toList());

        // 4. Recent OPD appointments (last 5) for OPD_CONVERT flow
        List<AppointmentSummaryResponse> recentAppointments = appointmentRepository
                .findTopByPatientIdAndTenantIdOrderByAppointmentDateDesc(
                        patientId, tenantId, PageRequest.of(0, 5))
                .stream()
                .map(appt -> AppointmentSummaryResponse.builder()
                        .appointmentId(appt.getId())
                        .appointmentDate(appt.getAppointmentDate() != null
                                ? appt.getAppointmentDate().toString() : null)
                        .doctorName(appt.getDoctor() != null
                                ? appt.getDoctor().getUser().getFullName() : null)
                        .departmentName(appt.getDoctor() != null && appt.getDoctor().getDepartment() != null
                                ? appt.getDoctor().getDepartment().getName() : null)
                        .status(appt.getStatus() != null ? appt.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());

        log.info("IPD admission context loaded for patient {} (tenant {}): {} doctors, {} wards, {} policies",
                patientId, tenantId, doctors.size(), wards.size(), policies.size());

        return IpdBookingContextResponse.builder()
                .doctors(doctors)
                .wards(wards)
                .insurancePolicies(policies)
                .recentAppointments(recentAppointments)
                .build();
    }

    private DoctorSummary mapDoctor(Doctor doctor) {
        String name = doctor.getUser() != null ? doctor.getUser().getFullName() : "Unknown";
        String specialisation = doctor.getDepartment() != null ? doctor.getDepartment().getName() : null;
        return DoctorSummary.builder()
                .doctorId(doctor.getId())
                .doctorName(name)
                .qualification(doctor.getQualification())
                .specialisation(specialisation)
                .build();
    }
}
