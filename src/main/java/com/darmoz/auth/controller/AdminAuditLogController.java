package com.darmoz.auth.controller;

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

@RestController
@RequestMapping("/admin/api/audit-log")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping
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
