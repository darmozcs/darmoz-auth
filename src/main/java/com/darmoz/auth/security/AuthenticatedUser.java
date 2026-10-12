package com.darmoz.auth.security;

import java.util.UUID;

/**
 * Principal que {@link JwtAuthenticationFilter} pone en el SecurityContext a partir del access
 * token. applicationId puede ser null en tokens emitidos antes de que existiera ese claim.
 */
public record AuthenticatedUser(UUID userId, UUID applicationId, String email) {
}
