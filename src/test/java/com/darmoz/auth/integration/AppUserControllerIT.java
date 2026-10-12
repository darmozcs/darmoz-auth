package com.darmoz.auth.integration;

import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.request.AppCreateUserRequest;
import com.darmoz.auth.dto.request.ConfirmEmailWithCredentialsRequest;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.ErrorResponse;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.EmailDeliveryException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppUserControllerIT extends AbstractIntegrationTest {

    static final UUID VIVOS_APPLICATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PASSWORD = "Vivos1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DarmozMailClient darmozMailClient;

    private Application vivos;
    private Application laryon;
    private UUID appAdminId;
    private String appAdminToken;

    @BeforeEach
    void seedAppAdmin() {
        vivos = applicationRepository.findById(VIVOS_APPLICATION_ID).orElseThrow();
        laryon = applicationRepository.findByName("Laryon").orElseThrow();

        User appAdmin = seedUser(vivos, Set.of("SUPER_ADMIN", "APP_ADMIN"), true);
        appAdminId = appAdmin.getId();
        appAdminToken = login(appAdmin.getEmail(), PASSWORD, VIVOS_APPLICATION_ID).getBody().accessToken();
    }

    @Test
    void createSendsCodeLeavesUserUnverifiedAndConfirmWithCredentialsLogsIn() {
        String email = "store-admin-" + UUID.randomUUID() + "@darmoz.com";

        ResponseEntity<AdminUserResponse> created = restTemplate.exchange(
                "/apps/api/users", HttpMethod.POST,
                new HttpEntity<>(new AppCreateUserRequest(email, PASSWORD, Set.of("ADMIN")), bearerHeaders(appAdminToken)),
                AdminUserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().emailVerified()).isFalse();
        assertThat(created.getBody().roles()).containsExactly("ADMIN");
        assertThat(created.getBody().applicationId()).isEqualTo(VIVOS_APPLICATION_ID);

        String code = capturedCode(email);

        // Sin verificar y con unverified_login_limit=0 -> /login bloqueado.
        ResponseEntity<ErrorResponse> blockedLogin = login(email, PASSWORD, VIVOS_APPLICATION_ID, ErrorResponse.class);
        assertThat(blockedLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blockedLogin.getBody().error()).isEqualTo("EMAIL_NOT_VERIFIED");

        ResponseEntity<ErrorResponse> wrongPassword = restTemplate.exchange(
                "/verify-email/confirm-with-credentials", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailWithCredentialsRequest(email, "otraPassword1", code),
                        apiIdHeaders(VIVOS_APPLICATION_ID)),
                ErrorResponse.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().message()).isEqualTo("Email o password invalidos");

        ResponseEntity<AuthResponse> confirmed = restTemplate.exchange(
                "/verify-email/confirm-with-credentials", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailWithCredentialsRequest(email, PASSWORD, code),
                        apiIdHeaders(VIVOS_APPLICATION_ID)),
                AuthResponse.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().emailVerified()).isTrue();
        assertThat(confirmed.getBody().roles()).containsExactly("ADMIN");
        assertThat(confirmed.getBody().accessToken()).isNotBlank();

        ResponseEntity<String> alreadyVerified = restTemplate.exchange(
                "/verify-email/confirm-with-credentials", HttpMethod.POST,
                new HttpEntity<>(new ConfirmEmailWithCredentialsRequest(email, PASSWORD, code),
                        apiIdHeaders(VIVOS_APPLICATION_ID)),
                String.class);
        assertThat(alreadyVerified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(login(email, PASSWORD, VIVOS_APPLICATION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AdminUserResponse[]> list = restTemplate.exchange(
                "/apps/api/users", HttpMethod.GET, bearerEntity(appAdminToken), AdminUserResponse[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        AdminUserResponse listed = Arrays.stream(list.getBody())
                .filter(user -> user.email().equals(email)).findFirst().orElseThrow();
        assertThat(listed.emailVerified()).isTrue();
    }

    @Test
    void usersOfOtherApplicationsAreNotVisible() {
        User laryonUser = seedUser(laryon, Set.of("USER"), true);

        ResponseEntity<AdminUserResponse[]> list = restTemplate.exchange(
                "/apps/api/users", HttpMethod.GET, bearerEntity(appAdminToken), AdminUserResponse[].class);
        assertThat(list.getBody()).allMatch(user -> user.applicationId().equals(VIVOS_APPLICATION_ID));
        assertThat(list.getBody()).extracting(AdminUserResponse::id).doesNotContain(laryonUser.getId());

        String path = "/apps/api/users/" + laryonUser.getId();
        ResponseEntity<ErrorResponse> get = restTemplate.exchange(path, HttpMethod.GET,
                bearerEntity(appAdminToken), ErrorResponse.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get.getBody().message()).isEqualTo("Usuario no encontrado");

        ResponseEntity<String> patch = restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(false, null), bearerHeaders(appAdminToken)), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> delete = restTemplate.exchange(path, HttpMethod.DELETE,
                bearerEntity(appAdminToken), String.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> resend = restTemplate.exchange(path + "/verification-email", HttpMethod.POST,
                bearerEntity(appAdminToken), String.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(userRepository.findById(laryonUser.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void reservedRolesCannotBeAssigned() {
        for (String reservedRole : Set.of("APP_ADMIN", "SUPER")) {
            String email = "escalation-" + UUID.randomUUID() + "@darmoz.com";
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    "/apps/api/users", HttpMethod.POST,
                    new HttpEntity<>(new AppCreateUserRequest(email, PASSWORD, Set.of("ADMIN", reservedRole)),
                            bearerHeaders(appAdminToken)),
                    ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(userRepository.existsByApplicationIdAndEmail(VIVOS_APPLICATION_ID, email)).isFalse();
        }

        // Un rol de otra aplicacion (USER existe en Laryon, no en Vivos) no se resuelve.
        ResponseEntity<String> foreignRole = restTemplate.exchange(
                "/apps/api/users", HttpMethod.POST,
                new HttpEntity<>(new AppCreateUserRequest("foreign-" + UUID.randomUUID() + "@darmoz.com", PASSWORD,
                        Set.of("USER")), bearerHeaders(appAdminToken)),
                String.class);
        assertThat(foreignRole.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(darmozMailClient, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void nonAppAdminIsForbiddenAndAnonymousIsUnauthorized() {
        User storeAdmin = seedUser(vivos, Set.of("ADMIN"), true);
        String storeAdminToken = login(storeAdmin.getEmail(), PASSWORD, VIVOS_APPLICATION_ID).getBody().accessToken();

        ResponseEntity<String> forbidden = restTemplate.exchange(
                "/apps/api/users", HttpMethod.GET, bearerEntity(storeAdminToken), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> anonymous = restTemplate.getForEntity("/apps/api/users", String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void selfAndOtherAppAdminsCannotBeModified() {
        String self = "/apps/api/users/" + appAdminId;
        ResponseEntity<String> patchSelf = restTemplate.exchange(self, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(false, null), bearerHeaders(appAdminToken)), String.class);
        assertThat(patchSelf.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> deleteSelf = restTemplate.exchange(self, HttpMethod.DELETE,
                bearerEntity(appAdminToken), String.class);
        assertThat(deleteSelf.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        User otherAppAdmin = seedUser(vivos, Set.of("APP_ADMIN"), true);
        ResponseEntity<String> patchOther = restTemplate.exchange("/apps/api/users/" + otherAppAdmin.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(false, null), bearerHeaders(appAdminToken)), String.class);
        assertThat(patchOther.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<AdminUserResponse> getSelf = restTemplate.exchange(self, HttpMethod.GET,
                bearerEntity(appAdminToken), AdminUserResponse.class);
        assertThat(getSelf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getSelf.getBody().enabled()).isTrue();
    }

    @Test
    void disablingRevokesSessionsAndPasswordResetWorks() {
        User storeAdmin = seedUser(vivos, Set.of("ADMIN"), true);
        AuthResponse session = login(storeAdmin.getEmail(), PASSWORD, VIVOS_APPLICATION_ID).getBody();
        String path = "/apps/api/users/" + storeAdmin.getId();

        ResponseEntity<AdminUserResponse> disabled = restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(false, null), bearerHeaders(appAdminToken)),
                AdminUserResponse.class);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody().enabled()).isFalse();

        ResponseEntity<String> refresh = restTemplate.exchange("/refresh", HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(session.refreshToken()), apiIdHeaders(VIVOS_APPLICATION_ID)),
                String.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<VerifyResponse> verify = restTemplate.exchange("/verify", HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(session.accessToken(), VIVOS_APPLICATION_ID)), VerifyResponse.class);
        assertThat(verify.getBody().valid()).isFalse();
        assertThat(verify.getBody().reason()).isEqualTo("disabled");

        assertThat(login(storeAdmin.getEmail(), PASSWORD, VIVOS_APPLICATION_ID, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Reactivar + resetear password: entra con la nueva, no con la vieja.
        String newPassword = "NuevaPass123!";
        ResponseEntity<AdminUserResponse> reset = restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(true, newPassword), bearerHeaders(appAdminToken)),
                AdminUserResponse.class);
        assertThat(reset.getBody().enabled()).isTrue();
        assertThat(login(storeAdmin.getEmail(), PASSWORD, VIVOS_APPLICATION_ID, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        AuthResponse newSession = login(storeAdmin.getEmail(), newPassword, VIVOS_APPLICATION_ID).getBody();

        // Un cambio de password tambien revoca las sesiones abiertas.
        restTemplate.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(null, "OtraPass123!"), bearerHeaders(appAdminToken)),
                AdminUserResponse.class);
        ResponseEntity<String> refreshAfterReset = restTemplate.exchange("/refresh", HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(newSession.refreshToken()), apiIdHeaders(VIVOS_APPLICATION_ID)),
                String.class);
        assertThat(refreshAfterReset.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> deleted = restTemplate.exchange(path, HttpMethod.DELETE,
                bearerEntity(appAdminToken), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findById(storeAdmin.getId())).isEmpty();
    }

    @Test
    void mailFailureRollsBackUserCreation() {
        String email = "mail-down-" + UUID.randomUUID() + "@darmoz.com";
        doThrow(new EmailDeliveryException("No se pudo enviar el email de verificacion", null))
                .when(darmozMailClient).sendVerificationCode(eq(email), anyString());

        ResponseEntity<String> response = restTemplate.exchange(
                "/apps/api/users", HttpMethod.POST,
                new HttpEntity<>(new AppCreateUserRequest(email, PASSWORD, Set.of("ADMIN")), bearerHeaders(appAdminToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(userRepository.existsByApplicationIdAndEmail(VIVOS_APPLICATION_ID, email)).isFalse();
    }

    @Test
    void duplicateEmailIsConflict() {
        User existing = seedUser(vivos, Set.of("ADMIN"), true);
        ResponseEntity<String> response = restTemplate.exchange(
                "/apps/api/users", HttpMethod.POST,
                new HttpEntity<>(new AppCreateUserRequest(existing.getEmail(), PASSWORD, Set.of("ADMIN")),
                        bearerHeaders(appAdminToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resendVerificationEmailIsThrottledAndRejectsVerifiedUsers() {
        String email = "resend-" + UUID.randomUUID() + "@darmoz.com";
        ResponseEntity<AdminUserResponse> created = restTemplate.exchange(
                "/apps/api/users", HttpMethod.POST,
                new HttpEntity<>(new AppCreateUserRequest(email, PASSWORD, Set.of("ADMIN")), bearerHeaders(appAdminToken)),
                AdminUserResponse.class);
        String resendPath = "/apps/api/users/" + created.getBody().id() + "/verification-email";

        ResponseEntity<ErrorResponse> throttled = restTemplate.exchange(resendPath, HttpMethod.POST,
                bearerEntity(appAdminToken), ErrorResponse.class);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getBody().error()).isEqualTo("TOO_MANY_REQUESTS");

        backdateVerificationTokens(created.getBody().id());
        ResponseEntity<Void> resent = restTemplate.exchange(resendPath, HttpMethod.POST,
                bearerEntity(appAdminToken), Void.class);
        assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        User verified = seedUser(vivos, Set.of("ADMIN"), true);
        ResponseEntity<String> alreadyVerified = restTemplate.exchange(
                "/apps/api/users/" + verified.getId() + "/verification-email", HttpMethod.POST,
                bearerEntity(appAdminToken), String.class);
        assertThat(alreadyVerified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private User seedUser(Application application, Set<String> roleNames, boolean emailVerified) {
        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByApplicationIdAndName(application.getId(), name).orElseThrow())
                .collect(Collectors.toSet());
        User user = new User("user-" + UUID.randomUUID() + "@darmoz.com", passwordEncoder.encode(PASSWORD), roles,
                application);
        user.setEmailVerified(emailVerified);
        return userRepository.save(user);
    }

    private void backdateVerificationTokens(UUID userId) {
        jdbcTemplate.update("UPDATE auth_email_verification_tokens SET created_at = created_at - interval '5 minutes'"
                + " WHERE user_id = ?", userId);
    }

    private String capturedCode(String email) {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(darmozMailClient, atLeastOnce()).sendVerificationCode(eq(email), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        return codeCaptor.getValue();
    }

    private ResponseEntity<AuthResponse> login(String email, String password, UUID applicationId) {
        ResponseEntity<AuthResponse> response = login(email, password, applicationId, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private <T> ResponseEntity<T> login(String email, String password, UUID applicationId, Class<T> type) {
        return restTemplate.exchange("/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), apiIdHeaders(applicationId)), type);
    }

    private HttpHeaders apiIdHeaders(UUID applicationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API_ID", applicationId.toString());
        return headers;
    }

    private HttpHeaders bearerHeaders(String accessToken) {
        return bearerHeaders(accessToken, VIVOS_APPLICATION_ID);
    }

    private HttpHeaders bearerHeaders(String accessToken, UUID applicationId) {
        HttpHeaders headers = apiIdHeaders(applicationId);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private HttpEntity<Void> bearerEntity(String accessToken) {
        return new HttpEntity<>(bearerHeaders(accessToken));
    }
}
