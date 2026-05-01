package com.curamatrix.hsm.service;

import com.curamatrix.hsm.entity.PayerMaster;
import com.curamatrix.hsm.repository.PayerMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayerMasterService {

    private final PayerMasterRepository payerMasterRepository;

    public List<PayerMaster> getAllPayers() {
        return payerMasterRepository.findAllByOrderByInsurerNameAsc();
    }
}
