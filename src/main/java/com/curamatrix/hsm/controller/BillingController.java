package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.repository.BillingRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import com.curamatrix.hsm.repository.PatientRepository;
import com.curamatrix.hsm.service.BillingService;
import com.curamatrix.hsm.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final BillingRepository billingRepository;
    private final HospitalServiceRepository hospitalServiceRepository;
    private final PatientRepository patientRepository;

    // --- Registration / Case Paper Billing ---

    @PostMapping("/register/{patientId}")
    public ResponseEntity<Billing> createRegistrationBilling(
            @PathVariable Long patientId,
            @RequestBody(required = false) Map<String, String> body) {
        Long tenantId = TenantContext.getTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        String paymentMethod = body != null ? body.get("paymentMethod") : null;
        Billing billing = billingService.createRegistrationBilling(patient, tenantId, paymentMethod);
        return ResponseEntity.ok(billing);
    }

    // --- Invoice Endpoints ---

    @GetMapping("/invoices")
    public ResponseEntity<List<Billing>> getAllInvoices() {
        return ResponseEntity.ok(billingRepository.findAllByTenantIdOrderByCreatedAtDesc(TenantContext.getTenantId()));
    }

    @GetMapping("/invoices/patient/{patientId}")
    public ResponseEntity<List<Billing>> getPatientInvoices(@PathVariable Long patientId) {
        return ResponseEntity.ok(billingRepository.findAllByPatientIdAndTenantId(patientId, TenantContext.getTenantId()));
    }

    @PutMapping("/invoices/{id}/pay")
    public ResponseEntity<Void> markAsPaid(@PathVariable Long id) {
        billingService.markAsPaid(id, TenantContext.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/registration-status/{patientId}")
    public ResponseEntity<Boolean> checkRegistration(@PathVariable Long patientId) {
        return ResponseEntity.ok(billingService.isRegistrationValid(patientId, TenantContext.getTenantId()));
    }

    // --- Hospital Service Management (Admin) ---

    @GetMapping("/services")
    public ResponseEntity<List<HospitalService>> getServices() {
        return ResponseEntity.ok(hospitalServiceRepository.findAllByTenantIdAndActiveTrue(TenantContext.getTenantId()));
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<HospitalService> updateService(@PathVariable Long id, @RequestBody HospitalService serviceData) {
        HospitalService service = hospitalServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        
        service.setPrice(serviceData.getPrice());
        service.setServiceName(serviceData.getServiceName());
        service.setDescription(serviceData.getDescription());
        service.setValidityPeriodDays(serviceData.getValidityPeriodDays());
        
        return ResponseEntity.ok(hospitalServiceRepository.save(service));
    }
}
