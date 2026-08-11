package com.darmoz.auth.repository;

import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleIn(Collection<Role> roles);
}
