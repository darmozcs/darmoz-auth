package com.darmoz.auth.controller;

import com.darmoz.auth.dto.RequestMetadata;
import com.darmoz.auth.dto.request.ConfirmEmailVerificationRequest;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.LogoutRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_ID_HEADER = "API_ID";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestHeader(API_ID_HEADER) String apiId,
                                                  @Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestHeader(API_ID_HEADER) String apiId,
                                               @Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader(API_ID_HEADER) String apiId,
                                                 @Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {
        authService.logout(apiId, extractBearerToken(authorizationHeader), request.refreshToken(),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verify(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@RequestHeader(API_ID_HEADER) String apiId,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                         HttpServletRequest httpRequest) {
        authService.disableCurrentUser(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/request")
    public ResponseEntity<Void> requestEmailVerification(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            HttpServletRequest httpRequest) {
        authService.requestEmailVerification(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/confirm")
    public ResponseEntity<Void> confirmEmailVerification(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody ConfirmEmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        authService.confirmEmailVerification(apiId, extractBearerToken(authorizationHeader), request.code(),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
