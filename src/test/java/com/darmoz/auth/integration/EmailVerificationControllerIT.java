package com.darmoz.auth.integration;

import com.darmoz.auth.dto.request.ConfirmEmailVerificationRequest;
import com.darmoz.auth.dto.request.ConfirmEmailWithCredentialsRequest;
import com.darmoz.auth.dto.request.CredentialsRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailVerificationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accessToken = registerResponse.getBody().accessToken();

        ResponseEntity<ErrorResponse> blockedLogin = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                ErrorResponse.class);
        assertThat(blockedLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blockedLogin.getBody()).isNotNull();
        assertThat(blockedLogin.getBody().error()).isEqualTo("EMAIL_NOT_VERIFIED");

        ResponseEntity<Void> requestResponse = restTemplate.exchange(
                "/verify-email/request", HttpMethod.POST, bearerEntity(accessToken), Void.class);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(darmozMailClient).sendVerificationCode(org.mockito.ArgumentMatchers.eq(email), codeCaptor.capture());
        String rawCode = codeCaptor.getValue();
        assertThat(rawCode).matches("\\d{6}");

        ResponseEntity<String> badCodeResponse = restTemplate.exchange(
                "/verify-email/confirm", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailVerificationRequest("000000"), bearerHeaders(accessToken)), String.class);
        assertThat(badCodeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> confirmResponse = restTemplate.exchange(
                "/verify-email/confirm", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailVerificationRequest(rawCode), bearerHeaders(accessToken)), Void.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AuthResponse> loginAfterVerify = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(loginAfterVerify.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmWithCredentialsConsumesCodeAfterFiveFailedAttempts() {
        String email = "attempts-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";
        register(email, password);

        ResponseEntity<Void> resend = resendWithCredentials(email, password, Void.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String code = capturedCode(email);
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseEntity<ErrorResponse> wrong = confirmWithCredentials(email, password, wrongCode, ErrorResponse.class);
            assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        // Con 5 fallos el codigo quedo consumido: ni el correcto sirve ya.
        ResponseEntity<ErrorResponse> exhausted = confirmWithCredentials(email, password, code, ErrorResponse.class);
        assertThat(exhausted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        backdateVerificationTokens(email);
        assertThat(resendWithCredentials(email, password, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String newCode = capturedCode(email);

        ResponseEntity<AuthResponse> confirmed = confirmWithCredentials(email, password, newCode, AuthResponse.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().emailVerified()).isTrue();

        ResponseEntity<String> alreadyVerified = resendWithCredentials(email, password, String.class);
        assertThat(alreadyVerified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resendWithCredentialsIsThrottledAndRequiresValidCredentials() {
        String email = "throttle-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";
        register(email, password);

        ResponseEntity<ErrorResponse> wrongPassword = resendWithCredentials(email, "wrong-password", ErrorResponse.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().message()).isEqualTo("Email o password invalidos");

        assertThat(resendWithCredentials(email, password, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> throttled = resendWithCredentials(email, password, ErrorResponse.class);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getBody().error()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(throttled.getBody().status()).isEqualTo(429);

        ResponseEntity<ErrorResponse> confirmWrongPassword =
                confirmWithCredentials(email, "wrong-password", capturedCode(email), ErrorResponse.class);
        assertThat(confirmWrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void register(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private <T> ResponseEntity<T> resendWithCredentials(String email, String password, Class<T> type) {
        return restTemplate.exchange("/verify-email/resend-with-credentials", HttpMethod.POST,
                new HttpEntity<>(new CredentialsRequest(email, password), jsonHeaders()), type);
    }

    private <T> ResponseEntity<T> confirmWithCredentials(String email, String password, String code, Class<T> type) {
        return restTemplate.exchange("/verify-email/confirm-with-credentials", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailWithCredentialsRequest(email, password, code), jsonHeaders()), type);
    }

    private String capturedCode(String email) {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(darmozMailClient, atLeastOnce()).sendVerificationCode(eq(email), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    private void backdateVerificationTokens(String email) {
        jdbcTemplate.update("UPDATE auth_email_verification_tokens SET created_at = created_at - interval '5 minutes'"
                + " WHERE user_id = (SELECT id FROM auth_users WHERE email = ? AND application_id = ?)",
                email, applicationId);
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
