-- Bitacora de auditoria de los 6 endpoints publicos de autenticacion (register/login/refresh/
-- logout/verify/disable). Se audita tanto exito como fallo (result + failure_reason) porque un
-- log de auditoria de seguridad que solo registra exitos no sirve para detectar abuso.
--
-- application_id se guarda SIN foreign key, a proposito: mismo criterio que
-- auth_refresh_tokens.user_id (columna UUID cruda, sin @ManyToOne/FK en el entity Java) — un
-- registro de auditoria tiene que seguir siendo legible aunque la Application referenciada
-- cambie de nombre o se borre, y tiene que poder representar tambien el caso de un API_ID que
-- nunca resolvio a ninguna aplicacion conocida (application_id/application_name = NULL en ese
-- caso). application_name es una copia (snapshot) del nombre al momento del evento.
--
-- user_agent y failure_reason son TEXT (no VARCHAR con tope) deliberadamente: son campos de
-- texto libre no controlado por un admin (headers de cliente / mensajes de excepcion), y un
-- INSERT que falla por truncamiento seria, ironicamente, un fallo dentro de la propia
-- transaccion de auditoria.
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY,
    action VARCHAR(20) NOT NULL,
    result VARCHAR(10) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    application_id UUID,
    application_name VARCHAR(100),
    user_email VARCHAR(255),
    failure_reason TEXT,
    origin VARCHAR(255),
    host VARCHAR(255),
    user_agent TEXT,
    referer VARCHAR(255)
);

CREATE INDEX idx_auth_audit_log_occurred_at ON auth_audit_log(occurred_at);
CREATE INDEX idx_auth_audit_log_action ON auth_audit_log(action);
CREATE INDEX idx_auth_audit_log_application_id ON auth_audit_log(application_id);
