package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.*;
import com.curamatrix.hsm.repository.*;
import com.curamatrix.hsm.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.Optional;

@SpringBootTest
public class BillingDebugtest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private BillingItemRepository billingItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IpdBillingService ipdBillingService;

    @Test
    @org.springframework.transaction.annotation.Transactional
    public void testBillingItemsQuery() {
        System.out.println("DEBUG START - Billing Items in DB:");
        List<BillingItem> items = billingItemRepository.findAll();
        for (BillingItem item : items) {
            System.out.println("Item ID: " + item.getId() + 
                               ", Description: " + item.getDescription() + 
                               ", Status: " + item.getPaymentStatus() + 
                               ", Paid Amount: " + item.getPaidAmount() + 
                               ", Billing ID: " + item.getBilling().getId() + 
                               ", Patient ID: " + item.getBilling().getPatient().getId());
        }

        List<Payment> payments = paymentRepository.findAll();
        System.out.println("Total payments in DB: " + payments.size());
        for (Payment p : payments) {
            System.out.println("Payment ID: " + p.getId() + 
                               ", Amount: " + p.getAmount() + 
                               ", Method: " + p.getMethod() + 
                               ", Patient: " + p.getPatient().getId() + 
                               ", Billing Item: " + (p.getBillingItem() != null ? p.getBillingItem().getId() : "null"));
        }
        System.out.println("DEBUG END");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    public void testSettleItemPersistence() {
        List<BillingItem> items = billingItemRepository.findAll();
        if (items.isEmpty()) {
            System.out.println("No billing items in DB to test with.");
            return;
        }
        BillingItem pendingItem = items.stream()
                .filter(i -> i.getPaymentStatus() == com.curamatrix.hsm.enums.PaymentStatus.PENDING)
                .findFirst().orElse(null);
        if (pendingItem == null) {
            System.out.println("No pending billing items in DB to test with.");
            return;
        }

        Long patientId = pendingItem.getBilling().getPatient().getId();
        Long itemId = pendingItem.getId();

        System.out.println("Settle billing item " + itemId + " for patient " + patientId);
        
        long paymentCountBefore = paymentRepository.count();
        
        // Mock security context
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin@curamatrix.com", "password", 
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        // Set tenant context
        com.curamatrix.hsm.context.TenantContext.setTenantId(pendingItem.getBilling().getTenantId());

        try {
            // Run settlement
            ipdBillingService.settleChargeItem(patientId, itemId, "CASH");

            // Verify changes
            BillingItem updatedItem = billingItemRepository.findById(itemId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(com.curamatrix.hsm.enums.PaymentStatus.PAID, updatedItem.getPaymentStatus());
            org.junit.jupiter.api.Assertions.assertEquals(paymentCountBefore + 1, paymentRepository.count());
            
            List<Payment> newPayments = paymentRepository.findAll();
            Payment latestPayment = newPayments.get(newPayments.size() - 1);
            org.junit.jupiter.api.Assertions.assertEquals(itemId, latestPayment.getBillingItem().getId());
            
            System.out.println("TEST SUCCESS: Settle billing item correctly updated status to PAID and created Payment record.");
        } finally {
            com.curamatrix.hsm.context.TenantContext.clear();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
