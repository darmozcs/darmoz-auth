package com.darmoz.auth.service;

import com.darmoz.auth.config.JwtProperties;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String PERMISSION_SERVICE = "service";
    private static final String PERMISSION_METHOD = "method";
    private static final String PERMISSION_PATH = "path";

    private final PrivateKey signingKey;
    private final PublicKey verificationKey;
    private final JwtProperties properties;

    public JwtService(PrivateKey jwtSigningKey, PublicKey jwtVerificationKey, JwtProperties properties) {
        this.signingKey = jwtSigningKey;
        this.verificationKey = jwtVerificationKey;
        this.properties = properties;
    }

    public IssuedAccessToken generateAccessToken(User user, List<PermissionDto> permissions) {
        UUID jti = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtlSeconds(), ChronoUnit.SECONDS);

        List<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        List<Map<String, String>> permissionClaims = permissions.stream()
                .map(permission -> Map.of(
                        PERMISSION_SERVICE, permission.service(),
                        PERMISSION_METHOD, permission.method(),
                        PERMISSION_PATH, permission.path()))
                .collect(Collectors.toList());

        String token = Jwts.builder()
                .id(jti.toString())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissionClaims)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuer(properties.getIssuer())
                .issuedAt(java.util.Date.from(issuedAt))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();

        return new IssuedAccessToken(token, jti, expiresAt);
    }

    /**
     * Parsea y valida firma + expiracion. Lanza {@link ExpiredJwtException} (con los claims
     * igual accesibles) o {@link JwtException} (firma invalida / malformado) si el token no es
     * utilizable.
     */
    public ParsedAccessToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return toParsedAccessToken(claims);
    }

    /** Igual que {@link #parse}, pero devuelve los claims aun si el token esta expirado. */
    public ParsedAccessToken parseIgnoringExpiration(ExpiredJwtException expiredException) {
        return toParsedAccessToken(expiredException.getClaims());
    }

    @SuppressWarnings("unchecked")
    private ParsedAccessToken toParsedAccessToken(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        Set<String> roles = Set.copyOf((List<String>) claims.get(CLAIM_ROLES, List.class));
        List<Map<String, String>> rawPermissions = claims.get(CLAIM_PERMISSIONS, List.class);
        List<PermissionDto> permissions = rawPermissions == null ? List.of() : rawPermissions.stream()
                .map(claim -> new PermissionDto(
                        claim.get(PERMISSION_SERVICE), claim.get(PERMISSION_METHOD), claim.get(PERMISSION_PATH)))
                .collect(Collectors.toList());
        UUID jti = UUID.fromString(claims.getId());
        Instant expiresAt = claims.getExpiration().toInstant();
        return new ParsedAccessToken(userId, email, roles, permissions, jti, expiresAt);
    }

    public record IssuedAccessToken(String token, UUID jti, Instant expiresAt) {
    }

    public record ParsedAccessToken(UUID userId, String email, Set<String> roles, List<PermissionDto> permissions,
                                     UUID jti, Instant expiresAt) {
    }
}
