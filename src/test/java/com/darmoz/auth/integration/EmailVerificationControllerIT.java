package com.darmoz.auth.integration;

import com.darmoz.auth.dto.request.ConfirmEmailVerificationRequest;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.ErrorResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.service.DarmozMailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailVerificationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @MockBean
    private DarmozMailClient darmozMailClient;

    private UUID applicationId;

    @BeforeEach
    void seedApplication() {
        Application application = applicationRepository.findByName("Laryon").orElseThrow();
        applicationId = application.getId();
        // Con el default (0 logins de gracia), /login debe bloquear ya el primer intento.
        application.setUnverifiedLoginLimit(0);
        applicationRepository.save(application);

        doNothing().when(darmozMailClient).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void registerLoginBlockedRequestConfirmThenLoginSucceeds() {
        String email = "verify-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";

        ResponseEntity<AuthResponse> registerResponse = restTemplate.exchange(
                "/auth/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accessToken = registerResponse.getBody().accessToken();

        ResponseEntity<ErrorResponse> blockedLogin = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                ErrorResponse.class);
        assertThat(blockedLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blockedLogin.getBody()).isNotNull();
        assertThat(blockedLogin.getBody().error()).isEqualTo("EMAIL_NOT_VERIFIED");

        ResponseEntity<Void> requestResponse = restTemplate.exchange(
                "/auth/verify-email/request", HttpMethod.POST, bearerEntity(accessToken), Void.class);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(darmozMailClient).sendVerificationCode(org.mockito.ArgumentMatchers.eq(email), codeCaptor.capture());
        String rawCode = codeCaptor.getValue();
        assertThat(rawCode).matches("\\d{6}");

        ResponseEntity<String> badCodeResponse = restTemplate.exchange(
                "/auth/verify-email/confirm", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailVerificationRequest("000000"), bearerHeaders(accessToken)), String.class);
        assertThat(badCodeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> confirmResponse = restTemplate.exchange(
                "/auth/verify-email/confirm", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailVerificationRequest(rawCode), bearerHeaders(accessToken)), Void.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AuthResponse> loginAfterVerify = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(loginAfterVerify.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API_ID", applicationId.toString());
        return headers;
    }

    private HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("API_ID", applicationId.toString());
        return headers;
    }

    private HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("API_ID", applicationId.toString());
        return new HttpEntity<>(headers);
    }
}
