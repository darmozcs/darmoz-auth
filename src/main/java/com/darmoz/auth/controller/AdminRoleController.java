package com.darmoz.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Admin · Roles", description = "Requiere Bearer de un usuario SUPER de la aplicación de sistema (11111111-1111-1111-1111-111111111111).")
@RestController
@RequestMapping("/admin/api/roles")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @GetMapping
    @Operation(summary = "Listar roles")
    public List<RoleResponse> list() {
        return adminRoleService.list();
    }

    @PostMapping
    @Operation(summary = "Crear rol")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody AdminCreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRoleService.create(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar rol")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
