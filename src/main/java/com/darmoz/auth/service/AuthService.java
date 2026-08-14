package com.darmoz.auth.service;

import com.darmoz.auth.dto.RequestMetadata;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.entity.AuditResult;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RevokedAccessToken;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.ApplicationMismatchException;
import com.darmoz.auth.exception.InvalidCredentialsException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.exception.UserAlreadyExistsException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RevokedAccessTokenRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationRepository applicationRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        ApplicationRepository applicationRepository,
                        RevokedAccessTokenRepository revokedAccessTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService,
                        PermissionService permissionService,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationRepository = applicationRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponse register(String apiId, RegisterRequest request, RequestMetadata metadata) {
        Application application = null;
        try {
            application = requireApplication(apiId);

            User user;
            Optional<User> existing = userRepository.findByApplicationIdAndEmail(application.getId(), request.email());
            if (existing.isPresent()) {
                user = existing.get();
                if (user.isEnabled()) {
                    throw new UserAlreadyExistsException("Ya existe una cuenta con ese email");
                }
                user.setEnabled(true);
                userRepository.save(user);
            } else {
                Role defaultRole = roleRepository.findByApplicationIdAndName(application.getId(), DEFAULT_ROLE)
                        .orElseThrow(() -> new IllegalStateException(
                                "Rol base '" + DEFAULT_ROLE + "' no existe para la aplicacion " + application.getId()));
                user = new User(request.email(), passwordEncoder.encode(request.password()), Set.of(defaultRole), application);
                userRepository.save(user);
            }

            AuthResponse response = issueTokens(user);
            auditLogService.record(AuditAction.REGISTER, application, user.getEmail(), AuditResult.SUCCESS, null, metadata);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.REGISTER, application, request.email(), AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }
    }

    @Transactional
    public AuthResponse login(String apiId, LoginRequest request, RequestMetadata metadata) {
        Application application = null;
        try {
            application = requireApplication(apiId);

            User user = userRepository.findByApplicationIdAndEmail(application.getId(), request.email())
                    .orElseThrow(() -> new InvalidCredentialsException("Email o password invalidos"));

            if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new InvalidCredentialsException("Email o password invalidos");
            }
            AuthResponse response = issueTokens(user);
            auditLogService.record(AuditAction.LOGIN, application, user.getEmail(), AuditResult.SUCCESS, null, metadata);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.LOGIN, application, request.email(), AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }
    }

    @Transactional
    public AuthResponse refresh(String apiId, RefreshRequest request, RequestMetadata metadata) {
        Application application = null;
        String email = null;
        try {
            application = requireApplication(apiId);

            RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
            User user = userRepository.findById(rotation.userId())
                    .orElseThrow(() -> new InvalidCredentialsException("Usuario asociado al token ya no existe"));
            email = user.getEmail();

            if (!user.getApplication().getId().equals(application.getId())) {
                // El refresh token ya se roto (es valido); si no lo revocamos aca queda
                // una credencial usable pese a que esta request se rechaza.
                refreshTokenService.revoke(rotation.rawToken());
                throw new ApplicationMismatchException("El refresh token no pertenece a la aplicacion indicada");
            }

            List<PermissionDto> permissions = permissionService.resolve(user.getRoles());
            JwtService.IssuedAccessToken accessToken = jwtService.generateAccessToken(user, permissions);
            AuthResponse response = AuthResponse.of(user.getId(), user.getEmail(), roleNames(user), permissions,
                    accessToken.token(), rotation.rawToken(), accessToken.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
            auditLogService.record(AuditAction.REFRESH, application, email, AuditResult.SUCCESS, null, metadata);
            return response;
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.REFRESH, application, email, AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }
    }

    @Transactional
    public void logout(String apiId, String accessToken, String refreshToken, RequestMetadata metadata) {
        // Se valida que la aplicacion exista, pero deliberadamente no se compara contra el
        // token/refresh token: accessToken es opcional en este endpoint y revocar una sesion
        // que uno ya posee (secreto propio) no representa riesgo cross-tenant.
        Application application;
        try {
            application = requireApplication(apiId);
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.LOGOUT, null, null, AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }

        refreshTokenService.revoke(refreshToken);

        String email = null;
        if (accessToken != null) {
            try {
                JwtService.ParsedAccessToken parsed = jwtService.parse(accessToken);
                email = parsed.email();
                blockAccessToken(parsed.jti(), parsed.expiresAt());
            } catch (ExpiredJwtException e) {
                JwtService.ParsedAccessToken parsed = jwtService.parseIgnoringExpiration(e);
                email = parsed.email();
                blockAccessToken(parsed.jti(), parsed.expiresAt());
            } catch (JwtException ignored) {
                // access token invalido/malformado: nada que revocar, ni email que capturar
            }
        }

        auditLogService.record(AuditAction.LOGOUT, application, email, AuditResult.SUCCESS, null, metadata);
    }

    @Transactional(readOnly = true)
    public VerifyResponse verify(String apiId, String accessToken, RequestMetadata metadata) {
        Application application;
        try {
            application = requireApplication(apiId);
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.VERIFY, null, null, AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }

        if (accessToken == null || accessToken.isBlank()) {
            auditLogService.record(AuditAction.VERIFY, application, null, AuditResult.FAILURE, "missing_token", metadata);
            return VerifyResponse.invalid("missing_token");
        }
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parse(accessToken);
            if (revokedAccessTokenRepository.existsByJti(parsed.jti())) {
                auditLogService.record(AuditAction.VERIFY, application, parsed.email(), AuditResult.FAILURE, "revoked", metadata);
                return VerifyResponse.invalid("revoked");
            }
            if (parsed.applicationId() == null || !parsed.applicationId().equals(application.getId())) {
                auditLogService.record(AuditAction.VERIFY, application, parsed.email(), AuditResult.FAILURE, "application_mismatch", metadata);
                return VerifyResponse.invalid("application_mismatch");
            }
            auditLogService.record(AuditAction.VERIFY, application, parsed.email(), AuditResult.SUCCESS, null, metadata);
            return VerifyResponse.valid(parsed.userId(), parsed.email(), parsed.roles(),
                    parsed.permissions(), parsed.expiresAt());
        } catch (ExpiredJwtException e) {
            String email = jwtService.parseIgnoringExpiration(e).email();
            auditLogService.record(AuditAction.VERIFY, application, email, AuditResult.FAILURE, "expired", metadata);
            return VerifyResponse.invalid("expired");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            auditLogService.record(AuditAction.VERIFY, application, null, AuditResult.FAILURE, "invalid_signature", metadata);
            return VerifyResponse.invalid("invalid_signature");
        } catch (JwtException e) {
            auditLogService.record(AuditAction.VERIFY, application, null, AuditResult.FAILURE, "malformed", metadata);
            return VerifyResponse.invalid("malformed");
        }
    }

    @Transactional
    public void disableCurrentUser(String apiId, String accessToken, RequestMetadata metadata) {
        Application application = null;
        String email = null;
        try {
            application = requireApplication(apiId);

            JwtService.ParsedAccessToken parsed = jwtService.parse(accessToken);
            email = parsed.email();
            if (parsed.applicationId() == null || !parsed.applicationId().equals(application.getId())) {
                throw new ApplicationMismatchException("El access token no pertenece a la aplicacion indicada");
            }

            User user = userRepository.findById(parsed.userId())
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

            user.setEnabled(false);
            userRepository.save(user);

            refreshTokenService.revokeAllForUser(user.getId());
            blockAccessToken(parsed.jti(), parsed.expiresAt());

            auditLogService.record(AuditAction.DISABLE, application, user.getEmail(), AuditResult.SUCCESS, null, metadata);
        } catch (RuntimeException e) {
            auditLogService.record(AuditAction.DISABLE, application, email, AuditResult.FAILURE, e.getMessage(), metadata);
            throw e;
        }
    }

    private Application requireApplication(String apiId) {
        UUID applicationId;
        try {
            applicationId = UUID.fromString(apiId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Aplicacion no encontrada");
        }
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Aplicacion no encontrada"));
    }

    private void blockAccessToken(UUID jti, Instant expiresAt) {
        if (expiresAt.isBefore(Instant.now())) {
            return;
        }
        if (!revokedAccessTokenRepository.existsByJti(jti)) {
            revokedAccessTokenRepository.save(
                    new RevokedAccessToken(jti, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
        }
    }

    private AuthResponse issueTokens(User user) {
        List<PermissionDto> permissions = permissionService.resolve(user.getRoles());
        JwtService.IssuedAccessToken accessToken = jwtService.generateAccessToken(user, permissions);
        String refreshToken = refreshTokenService.issue(user);
        long expiresIn = accessToken.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        return AuthResponse.of(user.getId(), user.getEmail(), roleNames(user), permissions,
                accessToken.token(), refreshToken, expiresIn);
    }

    private Set<String> roleNames(User user) {
        return roleNames(user.getRoles());
    }

    private Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
