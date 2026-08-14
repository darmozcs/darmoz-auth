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
        OffsetDateTime fromInstant = from == null ? null : from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toInstant = to == null ? null : to.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

        Page<AuditLogResponse> result = authAuditLogRepository
                .search(action, applicationId, emailFilter, fromInstant, toInstant, pageable)
                .map(AuditLogResponse::of);
        return PageResponse.of(result);
    }
}
