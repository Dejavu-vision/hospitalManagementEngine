package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.Role;
import com.curamatrix.hsm.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
