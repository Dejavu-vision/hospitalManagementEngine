package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.CollectPaymentRequest;
import com.curamatrix.hsm.dto.response.BillingResponse;
import com.curamatrix.hsm.dto.response.BillingSummaryResponse;
import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.enums.BillingItemType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PartialRegistrationFeeBillingUnitTest {

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private BillInsuranceSplitRepository splitRepository;

    @Mock
    private HospitalServiceRepository hospitalServiceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PatientRegistrationRepository patientRegistrationRepository;

    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;

    @Mock
    private PatientFinancialAccountService accountService;

    @Mock
    private CatalogResolverService catalogResolver;

    @InjectMocks
    private BillingService billingService;

    private Long tenantId = 1L;
    private Patient patient;
    private Billing billing;

    @BeforeEach
    public void setUp() {
        TenantContext.setTenantId(tenantId);

        patient = Patient.builder()
                .id(100L)
                .firstName("John")
                .lastName("Doe")
                .patientCode("P-00100")
                .build();
        patient.setTenantId(tenantId);

        BillingItem item = BillingItem.builder()
                .id(1L)
                .description("Registration Fee")
                .itemType(BillingItemType.REGISTRATION)
                .amount(new BigDecimal("500.00"))
                .quantity(1)
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        List<BillingItem> items = new ArrayList<>();
        items.add(item);

        billing = Billing.builder()
                .id(10L)
                .invoiceNumber("INV-0001")
                .patient(patient)
                .totalAmount(new BigDecimal("500.00"))
                .netAmount(new BigDecimal("500.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .items(items)
                .build();
        billing.setTenantId(tenantId);
        item.setBilling(billing);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testCollectPartialPaymentCalculatesBalanceAndStatusAccurately() {
        when(billingRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(billing));
        when(billingRepository.save(any(Billing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Patient owes 500, but only pays 200
        CollectPaymentRequest request = CollectPaymentRequest.builder()
                .amount(new BigDecimal("200.00"))
                .paymentMethod("CASH")
                .remarks("Partial payment at registration")
                .build();

        BillingResponse response = billingService.collectPayment(10L, request, tenantId);

        assertNotNull(response);
        assertEquals(new BigDecimal("500.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("500.00"), response.getNetAmount());
        assertEquals(new BigDecimal("200.00"), response.getPaidAmount());
        // Remaining pending balance must be 300, NOT 500!
        assertEquals(new BigDecimal("300.00"), response.getBalanceAmount());
        assertEquals("PARTIAL", response.getPaymentStatus());

        verify(accountService).recalculateAccountStatus(patient, tenantId);
    }

    @Test
    public void testDashboardSummaryPendingBalanceIncludesPartialBills() {
        when(billingRepository.countByTenantId(tenantId)).thenReturn(1L);
        when(billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PENDING)).thenReturn(0L);
        when(billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PAID)).thenReturn(0L);
        when(billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.PARTIAL)).thenReturn(1L);
        when(billingRepository.countByTenantIdAndPaymentStatus(tenantId, PaymentStatus.CANCELLED)).thenReturn(0L);

        // When a bill is 500 total and 200 paid, sumPendingBalance must return 300, totalRevenue must return 200
        when(billingRepository.sumPendingBalanceByTenantId(tenantId)).thenReturn(new BigDecimal("300.00"));
        when(billingRepository.sumTotalPaidRevenueByTenantId(tenantId)).thenReturn(new BigDecimal("200.00"));
        when(billingRepository.sumPaidAmountToday(eq(tenantId), any(), any())).thenReturn(new BigDecimal("200.00"));

        BillingSummaryResponse summary = billingService.getDashboardSummary(tenantId);

        assertNotNull(summary);
        assertEquals(new BigDecimal("300.00"), summary.getPendingAmount(), "Pending amount must be remaining balance (300), not full amount (500)");
        assertEquals(new BigDecimal("200.00"), summary.getTotalRevenue(), "Revenue collected must include partial payments (200)");
        assertEquals(new BigDecimal("200.00"), summary.getCollectedToday());
        assertEquals(1L, summary.getPartialCount());
    }
}
