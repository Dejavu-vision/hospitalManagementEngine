package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByTenantId(Long tenantId);
}
