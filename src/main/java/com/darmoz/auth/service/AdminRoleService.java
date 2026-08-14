package com.darmoz.auth.service;

import com.darmoz.auth.config.SuperAdminBootstrap;
import com.darmoz.auth.dto.request.AdminCreateRoleRequest;
import com.darmoz.auth.dto.response.RoleResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RolePermissionRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminRoleService {

    private static final String SUPER_ROLE = "SUPER";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ApplicationRepository applicationRepository;

    public AdminRoleService(RoleRepository roleRepository, UserRepository userRepository,
                             RolePermissionRepository rolePermissionRepository,
                             ApplicationRepository applicationRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(RoleResponse::of).toList();
    }

    @Transactional
    public RoleResponse create(AdminCreateRoleRequest request) {
        Application application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new NotFoundException("Aplicacion no encontrada: " + request.applicationId()));
        if (roleRepository.existsByApplicationIdAndName(application.getId(), request.name())) {
            throw new ConflictException("Ya existe un rol con ese nombre para esta aplicacion");
        }
        // Evita que un rol literalmente llamado SUPER en otra aplicacion obtenga ROLE_SUPER y,
        // con eso, acceso a /admin/api/** via SecurityConfig.hasRole("SUPER") (esa regla es
        // global, no esta scoped por aplicacion).
        if (SUPER_ROLE.equals(request.name()) && !application.getId().equals(SuperAdminBootstrap.SYSTEM_APPLICATION_ID)) {
            throw new ConflictException("El rol SUPER solo puede existir en la aplicacion de sistema");
        }
        Role role = new Role(request.name(), request.description(), application);
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
