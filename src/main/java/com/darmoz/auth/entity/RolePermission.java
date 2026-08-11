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
@Table(name = "auth_role_permissions")
public class RolePermission {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String service;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "endpoint_pattern", nullable = false)
    private String endpointPattern;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RolePermission() {
    }

    public RolePermission(Role role, String service, String httpMethod, String endpointPattern) {
        this.role = role;
        this.service = service;
        this.httpMethod = httpMethod;
        this.endpointPattern = endpointPattern;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getService() {
        return service;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpointPattern() {
        return endpointPattern;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
