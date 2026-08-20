package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.Application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String serviceName,
        String name,
        String description,
        int unverifiedLoginLimit,
        OffsetDateTime createdAt
) {
    public static ApplicationResponse of(Application application) {
        return new ApplicationResponse(application.getId(), application.getServiceName(),
                application.getName(), application.getDescription(), application.getUnverifiedLoginLimit(),
                application.getCreatedAt());
    }
}
