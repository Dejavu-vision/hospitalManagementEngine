package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.PreAuthCoverageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreAuthCoverageItemRepository extends JpaRepository<PreAuthCoverageItem, Long> {
    List<PreAuthCoverageItem> findByPreAuthId(Long preAuthId);
    void deleteByPreAuthId(Long preAuthId);
}
