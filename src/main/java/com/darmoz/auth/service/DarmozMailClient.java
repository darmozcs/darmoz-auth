package com.darmoz.auth.service;

import com.darmoz.auth.config.DarmozMailProperties;
import com.darmoz.auth.exception.EmailDeliveryException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;

@Service
public class DarmozMailClient {

    private static final String VERIFY_ACCION = "VERIFY";

    private final RestClient darmozMailRestClient;
    private final DarmozMailProperties properties;

    public DarmozMailClient(RestClient darmozMailRestClient, DarmozMailProperties properties) {
        this.darmozMailRestClient = darmozMailRestClient;
        this.properties = properties;
    }

    public void sendVerificationCode(String toEmail, String code) {
        try {
            darmozMailRestClient.post()
                    .uri("/emails")
                    .header("X-Client-Id", properties.getClientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendEmailRequest(toEmail, null, VERIFY_ACCION, Map.of("code", code), null, null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailDeliveryException("No se pudo enviar el email de verificacion", e);
        }
    }

    /** Espejo local del SendEmailRequest de darmoz-mail (servicios separados, sin dependencia compartida). */
    private record SendEmailRequest(String to, String subject, String accion, Map<String, String> variables,
                                     String bodyHtml, Instant scheduledAt) {
    }
}
