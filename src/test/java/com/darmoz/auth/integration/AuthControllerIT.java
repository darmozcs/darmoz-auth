package com.darmoz.auth.integration;

import com.darmoz.auth.dto.request.ChangePasswordRequest;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.LogoutRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.ErrorResponse;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.AuditAction;
import com.darmoz.auth.entity.AuditResult;
import com.darmoz.auth.entity.AuthAuditLog;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.AuthAuditLogRepository;
import com.darmoz.auth.repository.RolePermissionRepository;
import com.darmoz.auth.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    private PermissionDto expectedPermission;
    private UUID applicationId;

    @BeforeEach
    void seedRolePermissions() {
        rolePermissionRepository.deleteAll();
        Application application = applicationRepository.findByName("Laryon").orElseThrow();
        applicationId = application.getId();
        // Este test ejercita el lifecycle general de auth (no verificacion de email), asi que
        // se sube el limite de logins sin verificar para que /login no quede bloqueado con
        // EMAIL_NOT_VERIFIED — ese flujo se cubre aparte en EmailVerificationControllerIT.
        application.setUnverifiedLoginLimit(Integer.MAX_VALUE);
        applicationRepository.save(application);
        Role userRole = roleRepository.findByApplicationIdAndName(applicationId, "USER").orElseThrow();
        rolePermissionRepository.save(new RolePermission(userRole, "nexora-api", "GET", "/api/products/**"));
        expectedPermission = new PermissionDto("nexora-api", "GET", "/api/products/**");
    }

    @Test
    void loginAcceptsTheEmailLocalPart() {
        String localPart = "local-" + UUID.randomUUID() + "+tienda1";
        String gmail = localPart + "@gmail.com";
        String hotmail = localPart + "@hotmail.com";
        register(gmail, "Gmail1234!");
        register(hotmail, "Hotmail1234!");

        // Misma parte local en dos usuarios: entra el que coincide con la password.
        assertThat(login(localPart, "Gmail1234!").getBody().email()).isEqualTo(gmail);
        assertThat(login(localPart, "Hotmail1234!").getBody().email()).isEqualTo(hotmail);
        assertThat(login(gmail, "Gmail1234!").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(loginStatus(localPart, "otra-password")).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Solo la parte local completa: un prefijo de ella no alcanza.
        assertThat(loginStatus("local-", "Gmail1234!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Los comodines LIKE se toman literalmente.
        assertThat(loginStatus("%", "Gmail1234!")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(localPart.replace("local", "l_cal"), "Gmail1234!")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void register(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<AuthResponse> login(String login, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(login, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private HttpStatus loginStatus(String login, String password) {
        return HttpStatus.valueOf(restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(login, password), jsonHeaders()),
                String.class).getStatusCode().value());
    }

    @Test
    void fullAuthLifecycle() {
        String email = "user-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";

        ResponseEntity<AuthResponse> registerResponse = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().roles()).containsExactly("USER");
        assertThat(registerResponse.getBody().permissions()).containsExactly(expectedPermission);

        ResponseEntity<String> duplicateResponse = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                String.class);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> badLoginResponse = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, "wrong-password"), jsonHeaders()),
                String.class);
        assertThat(badLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse tokens = loginResponse.getBody();
        assertThat(tokens).isNotNull();
        assertThat(tokens.permissions()).containsExactly(expectedPermission);

        ResponseEntity<VerifyResponse> verifyResponse = restTemplate.exchange(
                "/verify", HttpMethod.POST, bearerEntity(tokens.accessToken()), VerifyResponse.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody().valid()).isTrue();
        assertThat(verifyResponse.getBody().email()).isEqualTo(email);
        assertThat(verifyResponse.getBody().permissions()).containsExactly(expectedPermission);

        ResponseEntity<VerifyResponse> noTokenVerify = restTemplate.exchange(
                "/verify", HttpMethod.POST, apiIdOnlyEntity(), VerifyResponse.class);
        assertThat(noTokenVerify.getBody().valid()).isFalse();
        assertThat(noTokenVerify.getBody().reason()).isEqualTo("missing_token");

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.exchange(
                "/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(tokens.refreshToken()), jsonHeaders()),
                AuthResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse rotatedTokens = refreshResponse.getBody();
        assertThat(rotatedTokens).isNotNull();
        assertThat(rotatedTokens.refreshToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(rotatedTokens.permissions()).containsExactly(expectedPermission);

        ResponseEntity<String> reusedRefreshResponse = restTemplate.exchange(
                "/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(tokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(reusedRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> refreshAfterReuseDetectionResponse = restTemplate.exchange(
                "/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(rotatedTokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(refreshAfterReuseDetectionResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthResponse> secondLogin = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        AuthResponse freshTokens = secondLogin.getBody();
        assertThat(freshTokens).isNotNull();

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(freshTokens.accessToken());
        logoutHeaders.set("API_ID", applicationId.toString());
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/logout", HttpMethod.POST,
                new HttpEntity<>(new LogoutRequest(freshTokens.refreshToken()), logoutHeaders), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<VerifyResponse> verifyAfterLogout = restTemplate.exchange(
                "/verify", HttpMethod.POST, bearerEntity(freshTokens.accessToken()), VerifyResponse.class);
        assertThat(verifyAfterLogout.getBody().valid()).isFalse();
        assertThat(verifyAfterLogout.getBody().reason()).isEqualTo("revoked");

        ResponseEntity<String> refreshAfterLogoutResponse = restTemplate.exchange(
                "/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(freshTokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(refreshAfterLogoutResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        List<AuthAuditLog> loginSuccesses = authAuditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.LOGIN && entry.getResult() == AuditResult.SUCCESS)
                .filter(entry -> email.equals(entry.getUserEmail()))
                .toList();
        assertThat(loginSuccesses).isNotEmpty();

        boolean anyFailureRecorded = authAuditLogRepository.findAll().stream()
                .anyMatch(entry -> entry.getResult() == AuditResult.FAILURE);
        assertThat(anyFailureRecorded).isTrue();
    }

    @Test
    void registerDoesNotReactivateDisabledAccount() {
        String email = "disabled-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";
        AuthResponse tokens = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class).getBody();

        ResponseEntity<Void> disable = restTemplate.exchange(
                "/disable", HttpMethod.POST, bearerEntity(tokens.accessToken()), Void.class);
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> reRegister = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, "Other1234!"), jsonHeaders()),
                String.class);
        assertThat(reRegister.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> login = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordRevokesSessions() {
        String email = "password-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";
        String newPassword = "Nueva1234!";
        AuthResponse tokens = restTemplate.exchange(
                "/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class).getBody();

        ResponseEntity<String> noToken = restTemplate.exchange(
                "/password", HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest(password, newPassword), jsonHeaders()), String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponse> wrongCurrent = restTemplate.exchange(
                "/password", HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest("wrong-password", newPassword), bearerJsonHeaders(tokens.accessToken())),
                ErrorResponse.class);
        assertThat(wrongCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongCurrent.getBody().error()).isEqualTo("INVALID_CURRENT_PASSWORD");
        assertThat(wrongCurrent.getBody().message()).isEqualTo("La contrasena actual no es correcta");

        ResponseEntity<String> tooShort = restTemplate.exchange(
                "/password", HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest(password, "corta"), bearerJsonHeaders(tokens.accessToken())),
                String.class);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> changed = restTemplate.exchange(
                "/password", HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest(password, newPassword), bearerJsonHeaders(tokens.accessToken())),
                Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<VerifyResponse> verify = restTemplate.exchange(
                "/verify", HttpMethod.POST, bearerEntity(tokens.accessToken()), VerifyResponse.class);
        assertThat(verify.getBody().valid()).isFalse();
        assertThat(verify.getBody().reason()).isEqualTo("revoked");

        ResponseEntity<String> refresh = restTemplate.exchange(
                "/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(tokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> oldLogin = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                String.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthResponse> newLogin = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, newPassword), jsonHeaders()),
                AuthResponse.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders bearerJsonHeaders(String accessToken) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API_ID", applicationId.toString());
        return headers;
    }

    private HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("API_ID", applicationId.toString());
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> apiIdOnlyEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("API_ID", applicationId.toString());
        return new HttpEntity<>(headers);
    }
}
