package com.darmoz.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "auth_applications")
public class Application {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "unverified_login_limit", nullable = false)
    private int unverifiedLoginLimit = 0;

    protected Application() {
    }

    public Application(String serviceName, String name, String description) {
        this.serviceName = serviceName;
        this.name = name;
        this.description = description;
    }

    /** Reconstruye una instancia detached con id conocido (tests, mapeos). */
    public Application(UUID id, String serviceName, String name, String description) {
        this(serviceName, name, description);
        this.id = id;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public int getUnverifiedLoginLimit() {
        return unverifiedLoginLimit;
    }

    public void setUnverifiedLoginLimit(int unverifiedLoginLimit) {
        this.unverifiedLoginLimit = unverifiedLoginLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Application other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
