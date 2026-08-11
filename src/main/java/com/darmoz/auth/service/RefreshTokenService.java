package com.darmoz.auth.service;

import com.darmoz.auth.config.JwtProperties;
import com.darmoz.auth.entity.RefreshToken;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.InvalidRefreshTokenException;
import com.darmoz.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    public String issue(User user) {
        String rawToken = generateOpaqueToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
        RefreshToken entity = new RefreshToken(user.getId(), hash(rawToken), expiresAt);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Valida el refresh token entrante, lo rota (revoca el usado + emite uno nuevo) y devuelve el
     * nuevo token en texto plano junto con el userId al que pertenece.
     * <p>
     * Si el token entrante ya estaba revocado (reuso de una cadena vieja, señal de robo), revoca
     * TODOS los refresh tokens activos del usuario y rechaza la operación.
     */
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token invalido"));

        if (existing.isRevoked()) {
            revokeAllForUser(existing.getUserId());
            throw new InvalidRefreshTokenException("Refresh token reusado; se revocaron todas las sesiones");
        }
        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token expirado");
        }

        String newRawToken = generateOpaqueToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
        RefreshToken replacement = new RefreshToken(existing.getUserId(), hash(newRawToken), expiresAt);
        refreshTokenRepository.save(replacement);

        existing.revoke(replacement.getId());
        refreshTokenRepository.save(existing);

        return new RotationResult(existing.getUserId(), newRawToken);
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.revoke(null);
                    refreshTokenRepository.save(token);
                });
    }

    public void revokeAllForUser(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(null));
        refreshTokenRepository.saveAll(active);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record RotationResult(UUID userId, String rawToken) {
    }
}
