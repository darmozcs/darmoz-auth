package com.darmoz.auth.controller;

import com.darmoz.auth.dto.request.AdminCreateApplicationRequest;
import com.darmoz.auth.dto.request.AdminUpdateApplicationRequest;
import com.darmoz.auth.dto.response.ApplicationResponse;
import com.darmoz.auth.service.AdminApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/applications")
public class AdminApplicationController {

    private final AdminApplicationService adminApplicationService;

    public AdminApplicationController(AdminApplicationService adminApplicationService) {
        this.adminApplicationService = adminApplicationService;
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return adminApplicationService.list();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return adminApplicationService.get(id);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody AdminCreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminApplicationService.create(request));
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUpdateApplicationRequest request) {
        return adminApplicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
