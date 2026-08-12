package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminCreateRoleRequest;
import com.darmoz.auth.dto.response.RoleResponse;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.RolePermissionRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminRoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public AdminRoleService(RoleRepository roleRepository, UserRepository userRepository,
                             RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(RoleResponse::of).toList();
    }

    @Transactional
    public RoleResponse create(AdminCreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("Ya existe un rol con ese nombre");
        }
        Role role = new Role(request.name(), request.description());
        return RoleResponse.of(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new NotFoundException("Rol no encontrado: " + id);
        }
        if (userRepository.existsByRoles_Id(id)) {
            throw new ConflictException("El rol sigue asignado a usuarios; quitalo de todos antes de borrarlo");
        }
        if (rolePermissionRepository.existsByRole_Id(id)) {
            throw new ConflictException("El rol tiene permisos asociados; borralos antes de eliminar el rol");
        }
        roleRepository.deleteById(id);
    }
}
