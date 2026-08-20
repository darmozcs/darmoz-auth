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

    Optional<EmailVerificationToken> findByUserIdAndCodeHashAndConsumedAtIsNullAndExpiresAtAfter(
            UUID userId, String codeHash, OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
