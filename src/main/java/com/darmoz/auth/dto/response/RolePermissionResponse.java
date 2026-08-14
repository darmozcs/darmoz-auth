package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RolePermissionResponse(
        UUID id,
        UUID roleId,
        String role,
        UUID applicationId,
        String applicationName,
        String service,
        String httpMethod,
        String endpointPattern,
        OffsetDateTime createdAt
) {
    public static RolePermissionResponse of(RolePermission rolePermission) {
        Role role = rolePermission.getRole();
        Application application = role.getApplication();
        return new RolePermissionResponse(
                rolePermission.getId(),
                role.getId(),
                role.getName(),
                application.getId(),
                application.getName(),
                rolePermission.getService(),
                rolePermission.getHttpMethod(),
                rolePermission.getEndpointPattern(),
                rolePermission.getCreatedAt());
    }
}
