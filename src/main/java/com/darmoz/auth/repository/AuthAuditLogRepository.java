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
    // los valores reales, asi que cada parametro opcional usado en un patron "IS NULL OR ..."
    // necesita un tipo sin ambiguedad. "=" contra una columna text/varchar resuelve el tipo
    // "unknown" del parametro implicitamente, pero ">="/"<=" contra timestamptz no tiene esa
    // coercion implicita y falla con "could not determine data type of parameter" si no se
    // castea. Casteando UNA sola ocurrencia de cada parametro nombrado alcanza (Postgres/pgjdbc
    // resuelve todas las ocurrencias del mismo parametro al mismo tipo), pero se castean todas
    // por consistencia/robustez ante futuros cambios en la query.
    @Query(value = """
            SELECT * FROM auth_audit_log a
            WHERE (:action IS NULL OR a.action = CAST(:action AS varchar))
              AND (:applicationId IS NULL OR a.application_id = CAST(:applicationId AS uuid))
              AND (:email IS NULL OR LOWER(a.user_email) LIKE LOWER(CONCAT('%', CAST(:email AS varchar), '%')))
              AND (:from IS NULL OR a.occurred_at >= CAST(:from AS timestamptz))
              AND (:to IS NULL OR a.occurred_at <= CAST(:to AS timestamptz))
            ORDER BY a.occurred_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM auth_audit_log a
            WHERE (:action IS NULL OR a.action = CAST(:action AS varchar))
              AND (:applicationId IS NULL OR a.application_id = CAST(:applicationId AS uuid))
              AND (:email IS NULL OR LOWER(a.user_email) LIKE LOWER(CONCAT('%', CAST(:email AS varchar), '%')))
              AND (:from IS NULL OR a.occurred_at >= CAST(:from AS timestamptz))
              AND (:to IS NULL OR a.occurred_at <= CAST(:to AS timestamptz))
            """,
            nativeQuery = true)
    Page<AuthAuditLog> search(@Param("action") String action,
                               @Param("applicationId") UUID applicationId,
                               @Param("email") String email,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to,
                               Pageable pageable);
}
