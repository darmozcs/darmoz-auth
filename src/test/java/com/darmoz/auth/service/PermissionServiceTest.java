package com.darmoz.auth.service;

import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;
import com.darmoz.auth.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(rolePermissionRepository);
    }

    @Test
    void resolvesPermissionsForRoles() {
        when(rolePermissionRepository.findByRoleIn(any())).thenReturn(List.of(
                new RolePermission(Role.USER, "nexora-api", "GET", "/api/products/**"),
                new RolePermission(Role.ADMIN, "nexora-api", "DELETE", "/api/products/**")));

        List<PermissionDto> permissions = permissionService.resolve(Set.of(Role.USER, Role.ADMIN));

        assertThat(permissions).containsExactlyInAnyOrder(
                new PermissionDto("nexora-api", "GET", "/api/products/**"),
                new PermissionDto("nexora-api", "DELETE", "/api/products/**"));
    }

    @Test
    void deduplicatesOverlappingPermissionsAcrossRoles() {
        when(rolePermissionRepository.findByRoleIn(any())).thenReturn(List.of(
                new RolePermission(Role.USER, "nexora-api", "GET", "/api/products/**"),
                new RolePermission(Role.ADMIN, "nexora-api", "GET", "/api/products/**")));

        List<PermissionDto> permissions = permissionService.resolve(Set.of(Role.USER, Role.ADMIN));

        assertThat(permissions).containsExactly(new PermissionDto("nexora-api", "GET", "/api/products/**"));
    }

    @Test
    void returnsEmptyListWhenNoPermissionsMatch() {
        when(rolePermissionRepository.findByRoleIn(any())).thenReturn(List.of());

        List<PermissionDto> permissions = permissionService.resolve(Set.of(Role.USER));

        assertThat(permissions).isEmpty();
    }
}
