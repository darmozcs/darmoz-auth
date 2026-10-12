package com.darmoz.auth.controller;

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
@RestController
@RequestMapping("/apps/api/users")
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    public List<AdminUserResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return appUserService.list(caller);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return appUserService.get(caller, id);
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@AuthenticationPrincipal AuthenticatedUser caller,
                                                    @Valid @RequestBody AppCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appUserService.create(caller, request));
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                    @Valid @RequestBody AdminUpdateUserRequest request) {
        return appUserService.update(caller, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        appUserService.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verification-email")
    public ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable UUID id) {
        appUserService.resendVerificationEmail(caller, id);
        return ResponseEntity.noContent().build();
    }
}
