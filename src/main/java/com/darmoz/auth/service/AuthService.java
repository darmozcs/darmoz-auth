package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RevokedAccessToken;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.InvalidCredentialsException;
import com.darmoz.auth.exception.UserAlreadyExistsException;
import com.darmoz.auth.repository.RevokedAccessTokenRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;

    public AuthService(UserRepository userRepository,
                        RevokedAccessTokenRepository revokedAccessTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService,
                        PermissionService permissionService) {
        this.userRepository = userRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.permissionService = permissionService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Ya existe una cuenta con ese email");
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()), Set.of(Role.USER));
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Email o password invalidos"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email o password invalidos");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario asociado al token ya no existe"));

        List<PermissionDto> permissions = permissionService.resolve(user.getRoles());
        JwtService.IssuedAccessToken accessToken = jwtService.generateAccessToken(user, permissions);
        return AuthResponse.of(user.getId(), user.getEmail(), roleNames(user), permissions,
                accessToken.token(), rotation.rawToken(), accessToken.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        refreshTokenService.revoke(refreshToken);

        if (accessToken == null) {
            return;
        }
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parse(accessToken);
            blockAccessToken(parsed.jti(), parsed.expiresAt());
        } catch (ExpiredJwtException e) {
            JwtService.ParsedAccessToken parsed = jwtService.parseIgnoringExpiration(e);
            blockAccessToken(parsed.jti(), parsed.expiresAt());
        } catch (JwtException ignored) {
            // access token invalido/malformado: nada que revocar
        }
    }

    @Transactional(readOnly = true)
    public VerifyResponse verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return VerifyResponse.invalid("missing_token");
        }
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parse(accessToken);
            if (revokedAccessTokenRepository.existsByJti(parsed.jti())) {
                return VerifyResponse.invalid("revoked");
            }
            return VerifyResponse.valid(parsed.userId(), parsed.email(), roleNames(parsed.roles()),
                    parsed.permissions(), parsed.expiresAt());
        } catch (ExpiredJwtException e) {
            return VerifyResponse.invalid("expired");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            return VerifyResponse.invalid("invalid_signature");
        } catch (JwtException e) {
            return VerifyResponse.invalid("malformed");
        }
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
        return roles.stream().map(Enum::name).collect(Collectors.toSet());
    }
}
