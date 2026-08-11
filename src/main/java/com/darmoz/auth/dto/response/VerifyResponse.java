package com.darmoz.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record VerifyResponse(
        boolean valid,
        UUID userId,
        String email,
        Set<String> roles,
        List<PermissionDto> permissions,
        Instant expiresAt,
        String reason
) {
    public static VerifyResponse valid(UUID userId, String email, Set<String> roles, List<PermissionDto> permissions,
                                        Instant expiresAt) {
        return new VerifyResponse(true, userId, email, roles, permissions, expiresAt, null);
    }

    public static VerifyResponse invalid(String reason) {
        return new VerifyResponse(false, null, null, null, null, null, reason);
    }
}
