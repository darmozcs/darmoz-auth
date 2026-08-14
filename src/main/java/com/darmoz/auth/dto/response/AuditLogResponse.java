package com.darmoz.auth.dto.response;

import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.entity.AuditResult;
import com.darmoz.auth.entity.AuthAuditLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        AuditAction action,
        AuditResult result,
        OffsetDateTime occurredAt,
        UUID applicationId,
        String applicationName,
        String userEmail,
        String failureReason,
        String origin,
        String host,
        String userAgent,
        String referer
) {
    public static AuditLogResponse of(AuthAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getResult(), log.getOccurredAt(),
                log.getApplicationId(), log.getApplicationName(), log.getUserEmail(), log.getFailureReason(),
                log.getOrigin(), log.getHost(), log.getUserAgent(), log.getReferer());
    }
}
