package com.darmoz.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.darmoz.auth.dto.response.AuditLogResponse;
import com.darmoz.auth.dto.response.PageResponse;
import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.service.AdminAuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Admin · Auditoría", description = "Requiere Bearer de un usuario SUPER de la aplicación de sistema (11111111-1111-1111-1111-111111111111).")
@RestController
@RequestMapping("/admin/api/audit-log")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping
    @Operation(summary = "Consultar auditoría", description = "Paginada y con filtros.")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminAuditLogService.search(action, applicationId, email, from, to, page, size);
    }
}
