package com.darmoz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "darmoz.mail")
public class DarmozMailProperties {

    /** Base URL de darmoz-mail, incluye el context-path (ej. http://darmoz-mail:8080/darmoz-mail). */
    private String baseUrl;

    /** UUID de la fila mail_client_application que identifica a darmoz-auth ante darmoz-mail. */
    private String clientId;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
