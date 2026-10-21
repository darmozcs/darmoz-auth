package com.darmoz.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.darmoz.auth.dto.RequestMetadata;
import com.darmoz.auth.dto.request.ChangePasswordRequest;
import com.darmoz.auth.dto.request.ConfirmEmailVerificationRequest;
import com.darmoz.auth.dto.request.ConfirmEmailWithCredentialsRequest;
import com.darmoz.auth.dto.request.CredentialsRequest;
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

@Tag(name = "Auth", description = "API pública para las aplicaciones. Todos piden el header API_ID.")
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
    @Operation(summary = "Registrar usuario", description = "Crea el usuario con rol USER en la aplicación del API_ID y devuelve tokens. Password de 8 a 100 caracteres. 409 si el email ya existe en esa aplicación, también si la cuenta está deshabilitada.")
    public ResponseEntity<AuthResponse> register(@RequestHeader(API_ID_HEADER) String apiId,
                                                  @Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "`email` acepta también solo la parte antes del `@`. 401 con el mismo mensaje para email inexistente, password incorrecta o cuenta deshabilitada. 403 `EMAIL_NOT_VERIFIED` si el email no está verificado y se agotaron los logins de gracia de la aplicación.")
    public ResponseEntity<AuthResponse> login(@RequestHeader(API_ID_HEADER) String apiId,
                                               @Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Rota el refresh token: el recibido queda inválido. Reusar uno ya usado revoca todas las sesiones del usuario (401). Los roles y permisos se recalculan.")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader(API_ID_HEADER) String apiId,
                                                 @Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(apiId, request, RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token del body. Si además se manda el Bearer, bloquea el access token. Siempre 204.")
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
    @Operation(summary = "Validar un access token", description = "Validación remota: firma, expiración, revocación y aplicación. Siempre 200; el resultado va en `valid` y `reason` (`missing_token`, `expired`, `invalid_signature`, `malformed`, `revoked`, `application_mismatch`, `disabled`).")
    public ResponseEntity<VerifyResponse> verify(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verify(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/disable")
    @Operation(summary = "Deshabilitar mi cuenta", description = "Pone `enabled=false`, revoca todas las sesiones y bloquea el token actual. Solo un admin puede volver a habilitarla.")
    public ResponseEntity<Void> disable(@RequestHeader(API_ID_HEADER) String apiId,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                         HttpServletRequest httpRequest) {
        authService.disableCurrentUser(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/request")
    @Operation(summary = "Pedir código de verificación", description = "Envía por email un código de 6 dígitos e invalida el anterior. 409 si ya está verificado; 429 `TOO_MANY_REQUESTS` si el último se envió hace menos de 60 s; 502 si falló darmoz-mail.")
    public ResponseEntity<Void> requestEmailVerification(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            HttpServletRequest httpRequest) {
        authService.requestEmailVerification(apiId, extractBearerToken(authorizationHeader),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/confirm")
    @Operation(summary = "Confirmar email", description = "Código de 6 dígitos. Vence a los 15 minutos y se agota a los 5 intentos fallidos.")
    public ResponseEntity<Void> confirmEmailVerification(
            @RequestHeader(API_ID_HEADER) String apiId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody ConfirmEmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        authService.confirmEmailVerification(apiId, extractBearerToken(authorizationHeader), request.code(),
                RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/confirm-with-credentials")
    @Operation(summary = "Confirmar email con credenciales", description = "Sin Bearer, para quien todavía no puede loguearse (403 `EMAIL_NOT_VERIFIED`). Prueba email + password, confirma el código y devuelve los tokens igual que `/login`.")
    public ResponseEntity<AuthResponse> confirmEmailWithCredentials(
            @RequestHeader(API_ID_HEADER) String apiId,
            @Valid @RequestBody ConfirmEmailWithCredentialsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.confirmEmailWithCredentials(apiId, request,
                RequestMetadata.from(httpRequest)));
    }

    @PostMapping("/verify-email/resend-with-credentials")
    @Operation(summary = "Reenviar código con credenciales", description = "Sin Bearer. Prueba email + password y envía un código nuevo. 429 si pasaron menos de 60 s desde el último.")
    public ResponseEntity<Void> resendEmailVerificationWithCredentials(
            @RequestHeader(API_ID_HEADER) String apiId,
            @Valid @RequestBody CredentialsRequest request,
            HttpServletRequest httpRequest) {
        authService.resendEmailVerificationWithCredentials(apiId, request, RequestMetadata.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    @Operation(summary = "Cambiar password", description = "`newPassword` de 8 a 100. Revoca todas las sesiones y bloquea el token actual: hay que volver a loguearse. 400 `INVALID_CURRENT_PASSWORD` si la actual no coincide.")
    public ResponseEntity<Void> changePassword(@RequestHeader(API_ID_HEADER) String apiId,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                                @Valid @RequestBody ChangePasswordRequest request,
                                                HttpServletRequest httpRequest) {
        authService.changePassword(apiId, extractBearerToken(authorizationHeader), request,
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
