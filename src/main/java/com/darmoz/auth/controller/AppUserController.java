package com.darmoz.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.request.AppCreateUserRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.security.AuthenticatedUser;
import com.darmoz.auth.service.AppUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Admin de usuarios limitado a la aplicacion del token (rol APP_ADMIN, ver SecurityConfig). */
@Tag(name = "Usuarios de mi aplicación", description = "Requiere Bearer de un usuario con rol APP_ADMIN; no usa API_ID. Todo queda limitado a la aplicación del token: un usuario de otra aplicación responde 404.")
@RestController
@RequestMapping("/apps/api/users")
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios")
    public List<AdminUserResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return appUserService.list(caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver usuario")
    public AdminUserResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return appUserService.get(caller, id);
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Los roles se buscan en la aplicación del token (no se puede asignar APP_ADMIN ni SUPER). El usuario se crea sin verificar y recibe el código por email; si falla el envío (502) no se crea.")
    public ResponseEntity<AdminUserResponse> create(@AuthenticationPrincipal AuthenticatedUser caller,
                                                    @Valid @RequestBody AppCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appUserService.create(caller, request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar usuario", description = "Parcial: `enabled` y/o `password` (8 a 100). Deshabilitar o cambiar la password revoca sus sesiones. 409 sobre uno mismo o sobre otro APP_ADMIN.")
    public AdminUserResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                    @Valid @RequestBody AdminUpdateUserRequest request) {
        return appUserService.update(caller, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar usuario", description = "Revoca sus sesiones y lo borra. 409 sobre uno mismo o sobre otro APP_ADMIN.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        appUserService.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verification-email")
    @Operation(summary = "Reenviar código de verificación", description = "409 si ya está verificado; 429 si pasaron menos de 60 s desde el último código.")
    public ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable UUID id) {
        appUserService.resendVerificationEmail(caller, id);
        return ResponseEntity.noContent().build();
    }
}
