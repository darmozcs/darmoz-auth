package com.darmoz.auth.repository;

import com.darmoz.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByApplicationIdAndName(UUID applicationId, String name);

    boolean existsByApplicationIdAndName(UUID applicationId, String name);

    boolean existsByApplicationId(UUID applicationId);
}
