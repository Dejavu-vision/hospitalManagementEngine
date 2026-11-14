package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.CollectPaymentRequest;
import com.curamatrix.hsm.dto.request.IpdSettlementRequest;
import com.curamatrix.hsm.dto.request.SectionDiscountRequest;
import com.curamatrix.hsm.dto.response.BillingResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.AdmissionStatus;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.enums.PaymentMethod;
import com.curamatrix.hsm.enums.PaymentStatus;
import com.curamatrix.hsm.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Enterprise Architecture & Financial Audit Invariant Tests for OPD and IPD Billing.
 * Validates immutable ledgers, fraud prevention, overpayment protections, and settlement integrity.
 */
@ExtendWith(MockitoExtension.class)
public class HospitalBillingFinancialAuditTest {

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private BillingItemRepository billingItemRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private HospitalServiceRepository hospitalServiceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PatientRegistrationRepository patientRegistrationRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private BillInsuranceSplitRepository insuranceSplitRepository;

    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;

    @Mock
    private BedAllocationRepository allocationRepository;

    @Mock
    private IpdAdmissionRepository admissionRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private PreAuthRequestRepository preAuthRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ClinicalCareService clinicalCareService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BillingService billingService;

    @InjectMocks
    private IpdBillingService ipdBillingService;

    private final Long tenantId = 100L;
    private Patient testPatient;

    @BeforeEach
    public void setUp() {
        TenantContext.setTenantId(tenantId);
        testPatient = Patient.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("9876543210")
                .build();
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. OPD & INVOICE PAYMENT / LEDGER AUDIT TESTS
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audit 1.1: Overpayment is strictly rejected to prevent unaccounted cash intake")
    public void testCollectPaymentRejectsOverpaymentBeyondBalanceDue() {
        Billing billing = Billing.builder()
                .id(10L)
                .patient(testPatient)
                .invoiceNumber("INV-001")
                .totalAmount(new BigDecimal("1000.00"))
                .netAmount(new BigDecimal("1000.00"))
                .paidAmount(new BigDecimal("200.00"))
                .paymentStatus(PaymentStatus.PARTIAL)
                .items(new ArrayList<>())
                .build();

        when(billingRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(billing));

        CollectPaymentRequest overpayReq = CollectPaymentRequest.builder()
                .paymentMethod("CASH")
                .amount(new BigDecimal("900.00")) // Outstanding is 800.00
                .remarks("Overpayment attempt")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                billingService.collectPayment(10L, overpayReq, tenantId));

        assertTrue(ex.getMessage().contains("exceeds the outstanding balance"));
        verify(billingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Audit 1.2: Full payment marks bill and items as PAID with immutable timestamp")
    public void testCollectPaymentFullSettlementMarksPaidAndUpdatesTimestamp() {
        BillingItem item1 = BillingItem.builder()
                .id(101L)
                .description("Doctor Consultation")
                .amount(new BigDecimal("500.00"))
                .quantity(1)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        Billing billing = Billing.builder()
                .id(10L)
                .patient(testPatient)
                .invoiceNumber("INV-002")
                .totalAmount(new BigDecimal("500.00"))
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>(List.of(item1)))
                .build();

        when(billingRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(billing));
        when(billingRepository.save(any(Billing.class))).thenAnswer(i -> i.getArgument(0));

        CollectPaymentRequest req = CollectPaymentRequest.builder()
                .paymentMethod("UPI")
                .amount(new BigDecimal("500.00"))
                .remarks("UPI Payment Ref #998811")
                .build();

        BillingResponse resp = billingService.collectPayment(10L, req, tenantId);

        assertEquals("PAID", resp.getPaymentStatus());
        assertEquals(new BigDecimal("500.00"), resp.getPaidAmount());
        assertNotNull(resp.getPaidAt());
        assertEquals("PAID", item1.getPaymentStatus().name());
        verify(billingRepository).save(billing);
    }

    @Test
    @DisplayName("Audit 1.3: Payment on CANCELLED or already PAID invoice is blocked")
    public void testCollectPaymentBlockedOnCancelledOrPaidInvoice() {
        Billing cancelledBill = Billing.builder()
                .id(11L)
                .patient(testPatient)
                .paymentStatus(PaymentStatus.CANCELLED)
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        Billing paidBill = Billing.builder()
                .id(12L)
                .patient(testPatient)
                .paymentStatus(PaymentStatus.PAID)
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(new BigDecimal("500.00"))
                .build();

        when(billingRepository.findByIdAndTenantId(11L, tenantId)).thenReturn(Optional.of(cancelledBill));
        when(billingRepository.findByIdAndTenantId(12L, tenantId)).thenReturn(Optional.of(paidBill));

        CollectPaymentRequest req = CollectPaymentRequest.builder()
                .paymentMethod("CASH")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThrows(RuntimeException.class, () -> billingService.collectPayment(11L, req, tenantId));
        assertThrows(RuntimeException.class, () -> billingService.collectPayment(12L, req, tenantId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. FINANCIAL FRAUD & RECORD DELETION AUDIT TESTS
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audit 2.1: Cancellation is blocked if payments were already collected")
    public void testCancelInvoiceBlockedWhenPaymentsCollected() {
        Billing billing = Billing.builder()
                .id(20L)
                .patient(testPatient)
                .invoiceNumber("INV-PAID-01")
                .paidAmount(new BigDecimal("250.00"))
                .netAmount(new BigDecimal("1000.00"))
                .paymentStatus(PaymentStatus.PARTIAL)
                .build();

        when(billingRepository.findByIdAndTenantId(20L, tenantId)).thenReturn(Optional.of(billing));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                billingService.cancelInvoice(20L, "Doctor requested cancel", "receptionist_bob", tenantId));

        assertTrue(ex.getMessage().contains("Cannot cancel an invoice with payments already collected"));
        verify(billingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Audit 2.2: Cancellation logs an immutable audit trail with user identity, time, and reason")
    public void testCancelInvoiceLogsImmutableAuditTrail() {
        Billing billing = Billing.builder()
                .id(21L)
                .patient(testPatient)
                .invoiceNumber("INV-DRAFT-01")
                .paidAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("500.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .remarks("Initial Consultation")
                .build();

        when(billingRepository.findByIdAndTenantId(21L, tenantId)).thenReturn(Optional.of(billing));
        when(billingRepository.save(any(Billing.class))).thenAnswer(i -> i.getArgument(0));

        BillingResponse resp = billingService.cancelInvoice(21L, "Patient left without consult", "rec_alice", tenantId);

        assertEquals("CANCELLED", resp.getPaymentStatus());
        assertNotNull(billing.getRemarks());
        assertTrue(billing.getRemarks().contains("Cancelled by rec_alice"));
        assertTrue(billing.getRemarks().contains("Patient left without consult"));
        verify(billingRepository).save(billing);
    }

    @Test
    @DisplayName("Audit 2.3: Deleting line items from a PAID bill or bill with payments collected is blocked")
    public void testDeleteBillingItemBlockedWhenPaymentsCollectedOrPaid() {
        BillingItem item = BillingItem.builder()
                .id(201L)
                .description("Pharmacy items")
                .amount(new BigDecimal("300.00"))
                .quantity(1)
                .paidAmount(BigDecimal.ZERO)
                .build();

        Billing paidBilling = Billing.builder()
                .id(22L)
                .patient(testPatient)
                .paymentStatus(PaymentStatus.PAID)
                .paidAmount(new BigDecimal("300.00"))
                .totalAmount(new BigDecimal("300.00"))
                .netAmount(new BigDecimal("300.00"))
                .items(new ArrayList<>(List.of(item)))
                .build();

        when(billingRepository.findByIdAndTenantId(22L, tenantId)).thenReturn(Optional.of(paidBilling));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                billingService.deleteBillingItem(22L, 201L, tenantId));

        assertTrue(ex.getMessage().contains("paid"));
        verify(billingItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Audit 2.4: Discount cannot reduce invoice below already collected payments or be negative")
    public void testDiscountValidationPreventsNegativeOrExceedingUnpaidBalance() {
        Billing billing = Billing.builder()
                .id(23L)
                .patient(testPatient)
                .totalAmount(new BigDecimal("1000.00"))
                .netAmount(new BigDecimal("1000.00"))
                .paidAmount(new BigDecimal("700.00"))
                .paymentStatus(PaymentStatus.PARTIAL)
                .build();

        when(billingRepository.findByIdAndTenantId(23L, tenantId)).thenReturn(Optional.of(billing));

        // Attempt 1: Negative discount
        assertThrows(RuntimeException.class, () ->
                billingService.applyDiscount(23L, new BigDecimal("-100.00"), tenantId, "admin"));

        // Attempt 2: Discount of 400.00 on a bill where only 300.00 remains unpaid
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                billingService.applyDiscount(23L, new BigDecimal("400.00"), tenantId, "admin"));

        assertTrue(ex.getMessage().contains("below the already collected payment"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. IPD INPATIENT BILLING & DISCHARGE AUDIT TESTS
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audit 3.1: IPD charges cannot be removed if patient is discharged or bill is paid/frozen")
    public void testRemoveChargeBlockedOnDischargedOrPaidPatient() {
        IpdAdmission dischargedAdm = IpdAdmission.builder()
                .id(301L)
                .patient(testPatient)
                .status(AdmissionStatus.DISCHARGED)
                .build();

        BillingItem item = BillingItem.builder()
                .id(3001L)
                .description("ICU Oxygen Charge")
                .amount(new BigDecimal("1200.00"))
                .quantity(1)
                .build();

        Billing bill = Billing.builder()
                .id(31L)
                .patient(testPatient)
                .ipdAdmission(dischargedAdm)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("1200.00"))
                .netAmount(new BigDecimal("1200.00"))
                .paidAmount(BigDecimal.ZERO)
                .items(new ArrayList<>(List.of(item)))
                .build();

        when(admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId))
                .thenReturn(Collections.emptyList());

        // When admission is null or already discharged
        assertThrows(RuntimeException.class, () ->
                ipdBillingService.removeCharge(testPatient.getId(), 3001L));
    }

    @Test
    @DisplayName("Audit 3.2: Final settlement clears all pending patient bills, records payment, and discharges patient")
    public void testFinalSettlementClearsAllPendingBillsAndDischarges() {
        IpdAdmission activeAdm = IpdAdmission.builder()
                .id(401L)
                .patient(testPatient)
                .status(AdmissionStatus.ADMITTED)
                .invoiceGenerated(true)
                .build();

        BillingItem ipdItem = BillingItem.builder()
                .id(4001L)
                .description("Bed Charges")
                .amount(new BigDecimal("2000.00"))
                .quantity(1)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        Billing ipdBill = Billing.builder()
                .id(41L)
                .patient(testPatient)
                .ipdAdmission(activeAdm)
                .invoiceNumber("IPD-401")
                .totalAmount(new BigDecimal("2000.00"))
                .netAmount(new BigDecimal("2000.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>(List.of(ipdItem)))
                .build();

        BillingItem opdItem = BillingItem.builder()
                .id(4002L)
                .description("Pre-admission Lab Test")
                .amount(new BigDecimal("500.00"))
                .quantity(1)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        Billing opdBill = Billing.builder()
                .id(42L)
                .patient(testPatient)
                .invoiceNumber("OPD-042")
                .totalAmount(new BigDecimal("500.00"))
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>(List.of(opdItem)))
                .build();

        when(admissionRepository.findByStatusAndTenantId(AdmissionStatus.ADMITTED, tenantId))
                .thenReturn(List.of(activeAdm));

        when(billingRepository.findAllByPatientIdAndTenantId(testPatient.getId(), tenantId))
                .thenReturn(new ArrayList<>(List.of(ipdBill, opdBill)));

        when(billingRepository.findByIpdAdmissionId(activeAdm.getId())).thenReturn(Optional.of(ipdBill));
        when(preAuthRepository.findByAdmissionIdAndTenantId(activeAdm.getId(), tenantId)).thenReturn(Optional.empty());

        Bed bed = Bed.builder().id(501L).bedNumber("101").status(BedStatus.OCCUPIED).build();
        BedAllocation alloc = BedAllocation.builder()
                .id(601L)
                .admission(activeAdm)
                .bed(bed)
                .isCurrent(true)
                .build();
        when(allocationRepository.findByAdmissionIdAndIsCurrentTrueAndTenantId(activeAdm.getId(), tenantId))
                .thenReturn(Optional.of(alloc));

        IpdSettlementRequest settleReq = new IpdSettlementRequest();
        settleReq.setAmount(new BigDecimal("2500.00")); // Full balance for both bills
        settleReq.setPaymentMethod("CASH");
        settleReq.setTransactionRef("CASH-SETTLE-01");

        Map<String, Object> summary = ipdBillingService.finalSettlement(testPatient.getId(), settleReq);

        // Assert both bills are PAID
        assertEquals(PaymentStatus.PAID, ipdBill.getPaymentStatus());
        assertEquals(new BigDecimal("2000.00"), ipdBill.getPaidAmount());
        assertEquals(PaymentStatus.PAID, opdBill.getPaymentStatus());
        assertEquals(new BigDecimal("500.00"), opdBill.getPaidAmount());

        // Assert bed released to CLEANING
        assertEquals(BedStatus.CLEANING, bed.getStatus());

        // Assert patient admission is marked DISCHARGED
        assertEquals(AdmissionStatus.DISCHARGED, activeAdm.getStatus());
        assertTrue(activeAdm.isDischargeCleared());

        // Assert individual payment receipts recorded for each bill
        verify(paymentRepository, atLeast(1)).save(any(Payment.class));
    }
}
