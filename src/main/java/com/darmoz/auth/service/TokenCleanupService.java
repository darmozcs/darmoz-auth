package com.darmoz.auth.service;

import com.darmoz.auth.repository.EmailVerificationTokenRepository;
import com.darmoz.auth.repository.RefreshTokenRepository;
import com.darmoz.auth.repository.RevokedAccessTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository,
                                RevokedAccessTokenRepository revokedAccessTokenRepository,
                                EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    }

    @Transactional
    @Scheduled(fixedRate = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    public void purgeExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.deleteAllByExpiresAtBefore(now);
        revokedAccessTokenRepository.deleteAllByExpiresAtBefore(now);
        emailVerificationTokenRepository.deleteAllByExpiresAtBefore(now);
    }
}
