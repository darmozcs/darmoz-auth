package com.darmoz.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(UUID userId, String codeHash, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Marca el codigo como ya no utilizable: sea porque el usuario lo confirmo con exito,
     * sea porque se genero un codigo nuevo que lo reemplaza (invalidacion al reenviar).
     */
    public void consume() {
        this.consumedAt = OffsetDateTime.now();
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
