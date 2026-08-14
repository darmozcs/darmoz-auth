package com.darmoz.auth.repository;

import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.entity.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    @Query(value = """
            SELECT a FROM AuthAuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:applicationId IS NULL OR a.applicationId = :applicationId)
              AND (:email IS NULL OR LOWER(a.userEmail) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
              AND (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt <= :to)
            ORDER BY a.occurredAt DESC
            """,
            countQuery = """
            SELECT COUNT(a) FROM AuthAuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:applicationId IS NULL OR a.applicationId = :applicationId)
              AND (:email IS NULL OR LOWER(a.userEmail) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%')))
              AND (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt <= :to)
            """)
    Page<AuthAuditLog> search(@Param("action") AuditAction action,
                               @Param("applicationId") UUID applicationId,
                               @Param("email") String email,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to,
                               Pageable pageable);
}
