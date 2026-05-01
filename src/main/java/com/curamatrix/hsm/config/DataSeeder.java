package com.curamatrix.hsm.config;

import com.curamatrix.hsm.entity.PayerMaster;
import com.curamatrix.hsm.repository.PayerMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PayerMasterRepository payerMasterRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedPayerMaster();
    }

    private void seedPayerMaster() {
        if (payerMasterRepository.count() == 0) {
            log.info("Seeding Payer Master (Insurers & TPAs) data...");
            List<PayerMaster> payers = List.of(
                    PayerMaster.builder()
                            .insurerName("Star Health and Allied Insurance")
                            .tpaName("Star Health TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("HDFC ERGO General Insurance")
                            .tpaName("Vidal Health TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("ICICI Lombard")
                            .tpaName("Medi Assist TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("Niva Bupa Health Insurance")
                            .tpaName("In-house TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("Care Health Insurance")
                            .tpaName("Care Health TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("Aditya Birla Health Insurance")
                            .tpaName("Paramount TPA")
                            .build(),
                    PayerMaster.builder()
                            .insurerName("Bajaj Allianz General Insurance")
                            .tpaName("HealthIndia TPA")
                            .build()
            );
            payerMasterRepository.saveAll(payers);
            log.info("Successfully seeded {} payers.", payers.size());
        }
    }
}
