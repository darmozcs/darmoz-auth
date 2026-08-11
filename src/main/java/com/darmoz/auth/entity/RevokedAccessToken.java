package com.darmoz.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_revoked_access_tokens")
public class RevokedAccessToken {

    @Id
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected RevokedAccessToken() {
    }

    public RevokedAccessToken(UUID jti, OffsetDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public UUID getJti() {
        return jti;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
