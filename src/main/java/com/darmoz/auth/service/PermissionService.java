package com.darmoz.auth.service;

import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PermissionService {

    private final RolePermissionRepository rolePermissionRepository;

    public PermissionService(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> resolve(Set<Role> roles) {
        Set<PermissionDto> deduped = new LinkedHashSet<>();
        rolePermissionRepository.findByRoleIn(roles).forEach(permission ->
                deduped.add(new PermissionDto(permission.getService(), permission.getHttpMethod(), permission.getEndpointPattern())));
        return List.copyOf(deduped);
    }
}
