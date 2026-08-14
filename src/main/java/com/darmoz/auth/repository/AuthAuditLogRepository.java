package com.darmoz.auth.repository;

import com.darmoz.auth.entity.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    // Query nativa (no JPQL) a proposito: Postgres tipa la query en el parseo, antes de conocer
    // los valores reales. Cada ocurrencia de "?" es un parametro independiente para Postgres
    // (no comparten tipo entre si aunque vengan del mismo :nombre) asi que un patron bare
    // "? IS NULL" sin ningun otro uso que le de contexto de tipo falla con "could not determine
    // data type of parameter", sin importar que OTRA ocurrencia del mismo parametro si tenga un
    // CAST. Para action/applicationId/email esto no pasa porque "=" contra una columna
    // text/uuid resuelve el tipo del "unknown" implicitamente incluso para el chequeo IS NULL;
    // para from/to (comparados con >=/<=) esa coercion implicita no existe. En vez de seguir
    // peleando con el patron "IS NULL OR", from/to nunca viajan null: AdminAuditLogService les
    // pone limites (1970 / año 2999) cuando no hay filtro, asi el parametro es siempre un
    // OffsetDateTime concreto comparado directo contra la columna, sin ambiguedad posible.
    @Query(value = """
            SELECT * FROM auth_audit_log a
            WHERE (:action IS NULL OR a.action = CAST(:action AS varchar))
              AND (:applicationId IS NULL OR a.application_id = CAST(:applicationId AS uuid))
              AND (:email IS NULL OR LOWER(a.user_email) LIKE LOWER(CONCAT('%', CAST(:email AS varchar), '%')))
              AND a.occurred_at >= :from
              AND a.occurred_at <= :to
            ORDER BY a.occurred_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM auth_audit_log a
            WHERE (:action IS NULL OR a.action = CAST(:action AS varchar))
              AND (:applicationId IS NULL OR a.application_id = CAST(:applicationId AS uuid))
              AND (:email IS NULL OR LOWER(a.user_email) LIKE LOWER(CONCAT('%', CAST(:email AS varchar), '%')))
              AND a.occurred_at >= :from
              AND a.occurred_at <= :to
            """,
            nativeQuery = true)
    Page<AuthAuditLog> search(@Param("action") String action,
                               @Param("applicationId") UUID applicationId,
                               @Param("email") String email,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to,
                               Pageable pageable);
}
