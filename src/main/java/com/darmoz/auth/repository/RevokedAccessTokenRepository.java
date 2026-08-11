package com.darmoz.auth.repository;

import com.darmoz.auth.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, UUID> {

    boolean existsByJti(UUID jti);

    @Modifying
    @Query("DELETE FROM RevokedAccessToken r WHERE r.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
