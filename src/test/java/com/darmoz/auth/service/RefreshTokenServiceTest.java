package com.darmoz.auth.service;

import com.darmoz.auth.config.JwtProperties;
import com.darmoz.auth.entity.RefreshToken;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.InvalidRefreshTokenException;
import com.darmoz.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshTokenTtlDays(30);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
        userId = UUID.randomUUID();
    }

    @Test
    void issueSavesHashedTokenAndReturnsRawToken() {
        String rawToken = refreshTokenService.issue(userInstance());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        assertThat(rawToken).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        RefreshToken existing = new RefreshToken(userId, "irrelevant-hash", OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-token");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.rawToken()).isNotBlank();
        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Test
    void rotateWithUnknownTokenThrows() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateWithAlreadyRevokedTokenRevokesAllUserTokensAndThrows() {
        RefreshToken existing = new RefreshToken(userId, "irrelevant-hash", OffsetDateTime.now().plusDays(1));
        existing.revoke(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        RefreshToken otherActive = new RefreshToken(userId, "other-hash", OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(otherActive));

        assertThatThrownBy(() -> refreshTokenService.rotate("reused-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(otherActive.isRevoked()).isTrue();
        verify(refreshTokenRepository).saveAll(List.of(otherActive));
    }

    @Test
    void rotateWithExpiredTokenThrowsAndDoesNotIssueNewToken() {
        RefreshToken existing = new RefreshToken(userId, "irrelevant-hash", OffsetDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    private User userInstance() {
        return new User(userId, "user@darmoz.com", "hash", Set.of(new Role("USER")));
    }
}
