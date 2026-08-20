package com.darmoz.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
public class AuthAuditLog {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditResult result;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    // Sin @ManyToOne / FK a proposito (mismo criterio que RefreshToken.userId): un registro de
    // auditoria tiene que seguir siendo legible aunque la Application referenciada cambie de
    // nombre o se borre, y tiene que poder representar tambien un API_ID que nunca resolvio a
    // ninguna aplicacion conocida (applicationId/applicationName = null en ese caso).
    // applicationName es una copia (snapshot) del nombre al momento del evento.
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "application_name")
    private String applicationName;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "failure_reason")
    private String failureReason;

    private String origin;

    private String host;

    @Column(name = "user_agent")
    private String userAgent;

    private String referer;

    protected AuthAuditLog() {
    }

    public AuthAuditLog(AuditAction action, AuditResult result, UUID applicationId, String applicationName,
                         String userEmail, String failureReason, String origin, String host, String userAgent,
                         String referer) {
        this.action = action;
        this.result = result;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.userEmail = userEmail;
        this.failureReason = failureReason;
        this.origin = origin;
        this.host = host;
        this.userAgent = userAgent;
        this.referer = referer;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditResult getResult() {
        return result;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getOrigin() {
        return origin;
    }

    public String getHost() {
        return host;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferer() {
        return referer;
    }
}
