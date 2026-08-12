package com.darmoz.auth.controller;

import com.darmoz.auth.dto.request.AdminAssignRolesRequest;
import com.darmoz.auth.dto.request.AdminCreateUserRequest;
import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> list() {
        return adminUserService.list();
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return adminUserService.update(id, request);
    }

    @PutMapping("/{id}/roles")
    public AdminUserResponse replaceRoles(@PathVariable UUID id, @Valid @RequestBody AdminAssignRolesRequest request) {
        return adminUserService.replaceRoles(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
