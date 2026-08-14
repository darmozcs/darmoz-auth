package com.darmoz.auth.service;

import com.darmoz.auth.dto.RequestMetadata;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.entity.AuditResult;
import com.darmoz.auth.entity.AuthAuditLog;
import com.darmoz.auth.repository.AuthAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuthAuditLogRepository authAuditLogRepository;

    public AuditLogService(AuthAuditLogRepository authAuditLogRepository) {
        this.authAuditLogRepository = authAuditLogRepository;
    }

    /**
     * Registra un evento de auditoria en su PROPIA transaccion (REQUIRES_NEW), independiente de
     * la transaccion de negocio que la invoca: si esta ultima hace rollback (p.ej. login
     * invalido), el registro de auditoria tiene que sobrevivir igual.
     * <p>
     * Nunca propaga una excepcion: un fallo al auditar (constraint, DB caida, columna
     * truncada, etc.) jamas debe convertir una operacion de negocio exitosa -o su error de
     * negocio esperado- en un 500 inesperado y no relacionado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, Application application, String userEmail,
                        AuditResult result, String failureReason, RequestMetadata metadata) {
        try {
            AuthAuditLog entry = new AuthAuditLog(
                    action,
                    result,
                    application == null ? null : application.getId(),
                    application == null ? null : application.getName(),
                    userEmail,
                    failureReason,
                    metadata == null ? null : metadata.origin(),
                    metadata == null ? null : metadata.host(),
                    metadata == null ? null : metadata.userAgent(),
                    metadata == null ? null : metadata.referer());
            authAuditLogRepository.save(entry);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el evento de auditoria action={} result={}", action, result, e);
        }
    }
}
