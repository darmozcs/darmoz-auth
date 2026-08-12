package com.darmoz.auth.controller;

import com.darmoz.auth.dto.request.AdminCreateRoleRequest;
import com.darmoz.auth.dto.response.RoleResponse;
import com.darmoz.auth.service.AdminRoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/roles")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return adminRoleService.list();
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody AdminCreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRoleService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
