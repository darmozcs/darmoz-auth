package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        UUID applicationId,
        String applicationName,
        OffsetDateTime createdAt
) {
    public static RoleResponse of(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.getApplication().getId(), role.getApplication().getName(), role.getCreatedAt());
    }
}
