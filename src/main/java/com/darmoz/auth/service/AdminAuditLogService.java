package com.darmoz.auth.service;

import com.darmoz.auth.dto.response.AuditLogResponse;
import com.darmoz.auth.dto.response.PageResponse;
import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.repository.AuthAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AdminAuditLogService {

    private static final int MAX_PAGE_SIZE = 200;

    // from/to nunca viajan null al repositorio (ver comentario en AuthAuditLogRepository):
    // limites bien lejos de cualquier dato real, para que "sin filtro de fecha" sea "todo el
    // rango posible" en vez de depender de un chequeo IS NULL ambiguo para Postgres.
    private static final OffsetDateTime MIN_TIMESTAMP = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_TIMESTAMP = OffsetDateTime.of(2999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private final AuthAuditLogRepository authAuditLogRepository;

    public AdminAuditLogService(AuthAuditLogRepository authAuditLogRepository) {
        this.authAuditLogRepository = authAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action, UUID applicationId, String email,
                                                  LocalDate from, LocalDate to, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Sin Sort en el Pageable a proposito: el ORDER BY ya esta fijo en
        // AuthAuditLogRepository.search (occurredAt DESC); agregar sort dinamico ahi arriesga
        // un segundo ORDER BY invalido.
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        String emailFilter = (email == null || email.isBlank()) ? null : email;
        OffsetDateTime fromInstant = from == null ? MIN_TIMESTAMP : from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toInstant = to == null ? MAX_TIMESTAMP : to.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

        String actionFilter = action == null ? null : action.name();
        Page<AuditLogResponse> result = authAuditLogRepository
                .search(actionFilter, applicationId, emailFilter, fromInstant, toInstant, pageable)
                .map(AuditLogResponse::of);
        return PageResponse.of(result);
    }
}
