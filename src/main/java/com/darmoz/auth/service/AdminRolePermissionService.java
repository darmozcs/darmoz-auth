package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminCreateRolePermissionRequest;
import com.darmoz.auth.dto.response.RolePermissionResponse;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.RolePermissionRepository;
import com.darmoz.auth.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminRolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    public AdminRolePermissionService(RolePermissionRepository rolePermissionRepository, RoleRepository roleRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RolePermissionResponse> list(String roleName) {
        List<RolePermission> permissions;
        if (roleName == null || roleName.isBlank()) {
            permissions = rolePermissionRepository.findAll();
        } else {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new NotFoundException("Rol no encontrado: " + roleName));
            permissions = rolePermissionRepository.findByRoleIn(List.of(role));
        }
        return permissions.stream().map(RolePermissionResponse::of).toList();
    }

    @Transactional
    public RolePermissionResponse create(AdminCreateRolePermissionRequest request) {
        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new NotFoundException("Rol no encontrado: " + request.role()));
        boolean exists = rolePermissionRepository.existsByRoleAndServiceAndHttpMethodAndEndpointPattern(
                role, request.service(), request.httpMethod(), request.endpointPattern());
        if (exists) {
            throw new ConflictException("Ya existe ese permiso para el rol");
        }
        RolePermission saved = rolePermissionRepository.save(
                new RolePermission(role, request.service(), request.httpMethod(), request.endpointPattern()));
        return RolePermissionResponse.of(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!rolePermissionRepository.existsById(id)) {
            throw new NotFoundException("Permiso no encontrado: " + id);
        }
        rolePermissionRepository.deleteById(id);
    }
}
