package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AdminUserResponse(
        UUID id,
        String email,
        boolean enabled,
        boolean emailVerified,
        int unverifiedLoginCount,
        UUID applicationId,
        String applicationName,
        Set<String> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AdminUserResponse of(User user) {
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new AdminUserResponse(user.getId(), user.getEmail(), user.isEnabled(),
                user.isEmailVerified(), user.getUnverifiedLoginCount(),
                user.getApplication().getId(), user.getApplication().getName(), roleNames,
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
