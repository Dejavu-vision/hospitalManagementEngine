package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.entity.Billing;
import com.curamatrix.hsm.entity.Patient;
import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IpdAdmissionServiceRowBalanceUnitTest {

    @Mock
    private IpdAdmissionRepository admissionRepository;

    @Mock
    private BedAllocationRepository allocationRepository;

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private BedRepository bedRepository;

    @InjectMocks
    private IpdAdmissionService ipdAdmissionService;

    private Long tenantId = 55L;

    @BeforeEach
    public void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testGetActiveAdmissionsPendingTabIncludesOpdBalanceAmountAndInvoiceId() {
        Patient patient = Patient.builder()
                .id(200L)
                .firstName("Alice")
                .lastName("Smith")
                .patientCode("P-200")
                .build();
        patient.setTenantId(tenantId);

        Billing opdBill = Billing.builder()
                .id(999L)
                .invoiceNumber("INV-OPD-999")
                .patient(patient)
                .totalAmount(new BigDecimal("500.00"))
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(new BigDecimal("200.00"))
                .paymentStatus(PaymentStatus.PARTIAL)
                .build();
        opdBill.setTenantId(tenantId);

        when(admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId))
                .thenReturn(Collections.emptyList());
        when(billingRepository.findUnpaidDischargedBills(eq(tenantId), anyList()))
                .thenReturn(Collections.emptyList());
        when(billingRepository.findPendingOpdBills(eq(tenantId), anyList()))
                .thenReturn(List.of(opdBill));

        List<Map<String, Object>> result = ipdAdmissionService.getActiveAdmissions("PENDING", null, null);

        assertNotNull(result);
        assertEquals(1, result.size());

        Map<String, Object> row = result.get(0);
        assertEquals(200L, row.get("patientId"));
        assertEquals("OPD", row.get("admissionNumber"));
        assertEquals("INV-OPD-999", row.get("invoiceNumber"));
        assertEquals(999L, row.get("invoiceId"));
        assertEquals(new BigDecimal("500.00"), row.get("runningBillTotal"));
        assertEquals(new BigDecimal("200.00"), row.get("paidAmount"));
        // Key validation: balanceAmount must be 300, status must be PARTIAL
        assertEquals(new BigDecimal("300.00"), row.get("balanceAmount"));
        assertEquals("PARTIAL", row.get("paymentStatus"));
    }
}
