package com.darmoz.auth.controller;

import com.darmoz.auth.dto.request.AdminCreateRolePermissionRequest;
import com.darmoz.auth.dto.response.RolePermissionResponse;
import com.darmoz.auth.service.AdminRolePermissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/role-permissions")
public class AdminRolePermissionController {

    private final AdminRolePermissionService adminRolePermissionService;

    public AdminRolePermissionController(AdminRolePermissionService adminRolePermissionService) {
        this.adminRolePermissionService = adminRolePermissionService;
    }

    @GetMapping
    public List<RolePermissionResponse> list(@RequestParam(required = false) String role) {
        return adminRolePermissionService.list(role);
    }

    @PostMapping
    public ResponseEntity<RolePermissionResponse> create(@Valid @RequestBody AdminCreateRolePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRolePermissionService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminRolePermissionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
