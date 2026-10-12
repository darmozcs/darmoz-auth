package com.darmoz.auth.repository;

import com.darmoz.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    List<EmailVerificationToken> findAllByUserIdAndConsumedAtIsNullAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    /** Codigo activo (no consumido, no vencido) mas reciente del usuario. */
    Optional<EmailVerificationToken> findFirstByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, OffsetDateTime now);

    /** Ultimo codigo emitido al usuario (en cualquier estado), para el throttle de reenvio. */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
