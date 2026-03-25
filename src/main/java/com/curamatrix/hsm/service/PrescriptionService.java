package com.curamatrix.hsm.service;

import com.curamatrix.hsm.dto.request.PrescriptionBatchRequest;
import com.curamatrix.hsm.dto.response.PrescriptionResponse;
import com.curamatrix.hsm.entity.Diagnosis;
import com.curamatrix.hsm.entity.Medicine;
import com.curamatrix.hsm.entity.Prescription;
import com.curamatrix.hsm.repository.DiagnosisRepository;
import com.curamatrix.hsm.repository.MedicineRepository;
import com.curamatrix.hsm.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.curamatrix.hsm.exception.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final MedicineRepository medicineRepository;

    @Transactional
    public List<PrescriptionResponse> addPrescriptions(PrescriptionBatchRequest request) {
        Diagnosis diagnosis = diagnosisRepository.findById(request.getDiagnosisId())
                .orElseThrow(() -> new ResourceNotFoundException("Diagnosis", "id", request.getDiagnosisId()));

        List<Prescription> prescriptions = request.getPrescriptions().stream()
                .map(item -> {
                    Medicine medicine = medicineRepository.findById(item.getMedicineId())
                            .orElseThrow(() -> new ResourceNotFoundException("Medicine", "id", item.getMedicineId()));

                    return Prescription.builder()
                            .diagnosis(diagnosis)
                            .medicine(medicine)
                            .dosage(item.getDosage())
                            .frequency(item.getFrequency())
                            .durationDays(item.getDurationDays())
                            .instructions(item.getInstructions())
                            .build();
                })
                .collect(Collectors.toList());

        prescriptions = prescriptionRepository.saveAll(prescriptions);
        log.info("Added {} prescriptions for diagnosis: {}", prescriptions.size(), request.getDiagnosisId());

        return prescriptions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PrescriptionResponse> getPrescriptionsByDiagnosisId(Long diagnosisId) {
        return prescriptionRepository.findByDiagnosisId(diagnosisId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PrescriptionResponse mapToResponse(Prescription prescription) {
        return PrescriptionResponse.builder()
                .id(prescription.getId())
                .medicineId(prescription.getMedicine().getId())
                .medicineName(prescription.getMedicine().getName())
                .medicineStrength(prescription.getMedicine().getStrength())
                .medicineForm(prescription.getMedicine().getForm())
                .dosage(prescription.getDosage())
                .frequency(prescription.getFrequency())
                .durationDays(prescription.getDurationDays())
                .instructions(prescription.getInstructions())
                .build();
    }
}
