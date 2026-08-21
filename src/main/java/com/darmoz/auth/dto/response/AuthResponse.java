package com.darmoz.auth.dto.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        Set<String> roles,
        List<PermissionDto> permissions,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean emailVerified,
        int unverifiedLoginLimit,
        int unverifiedLoginCount
) {
    public static AuthResponse of(UUID userId, String email, Set<String> roles, List<PermissionDto> permissions,
                                   String accessToken, String refreshToken, long expiresInSeconds,
                                   boolean emailVerified, int unverifiedLoginLimit, int unverifiedLoginCount) {
        return new AuthResponse(userId, email, roles, permissions, accessToken, refreshToken, "Bearer", expiresInSeconds,
                emailVerified, unverifiedLoginLimit, unverifiedLoginCount);
    }
}
