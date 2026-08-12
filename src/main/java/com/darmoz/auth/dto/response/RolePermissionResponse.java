package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.RolePermission;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RolePermissionResponse(
        UUID id,
        String role,
        String service,
        String httpMethod,
        String endpointPattern,
        OffsetDateTime createdAt
) {
    public static RolePermissionResponse of(RolePermission rolePermission) {
        return new RolePermissionResponse(
                rolePermission.getId(),
                rolePermission.getRole().getName(),
                rolePermission.getService(),
                rolePermission.getHttpMethod(),
                rolePermission.getEndpointPattern(),
                rolePermission.getCreatedAt());
    }
}
