package com.darmoz.auth.integration;

import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.LogoutRequest;
import com.darmoz.auth.dto.request.RefreshRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.dto.response.VerifyResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.RolePermission;
import com.darmoz.auth.repository.ApplicationRepository;
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

    private PermissionDto expectedPermission;
    private UUID applicationId;

    @BeforeEach
    void seedRolePermissions() {
        rolePermissionRepository.deleteAll();
        Application application = applicationRepository.findByName("Laryon").orElseThrow();
        applicationId = application.getId();
        Role userRole = roleRepository.findByApplicationIdAndName(applicationId, "USER").orElseThrow();
        rolePermissionRepository.save(new RolePermission(userRole, "nexora-api", "GET", "/api/products/**"));
        expectedPermission = new PermissionDto("nexora-api", "GET", "/api/products/**");
    }

    @Test
    void fullAuthLifecycle() {
        String email = "user-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Test1234!";

        ResponseEntity<AuthResponse> registerResponse = restTemplate.exchange(
                "/auth/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().roles()).containsExactly("USER");
        assertThat(registerResponse.getBody().permissions()).containsExactly(expectedPermission);

        ResponseEntity<String> duplicateResponse = restTemplate.exchange(
                "/auth/register", HttpMethod.POST, new HttpEntity<>(new RegisterRequest(email, password), jsonHeaders()),
                String.class);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> badLoginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, "wrong-password"), jsonHeaders()),
                String.class);
        assertThat(badLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse tokens = loginResponse.getBody();
        assertThat(tokens).isNotNull();
        assertThat(tokens.permissions()).containsExactly(expectedPermission);

        ResponseEntity<VerifyResponse> verifyResponse = restTemplate.exchange(
                "/auth/verify", HttpMethod.POST, bearerEntity(tokens.accessToken()), VerifyResponse.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody().valid()).isTrue();
        assertThat(verifyResponse.getBody().email()).isEqualTo(email);
        assertThat(verifyResponse.getBody().permissions()).containsExactly(expectedPermission);

        ResponseEntity<VerifyResponse> noTokenVerify = restTemplate.exchange(
                "/auth/verify", HttpMethod.POST, apiIdOnlyEntity(), VerifyResponse.class);
        assertThat(noTokenVerify.getBody().valid()).isFalse();
        assertThat(noTokenVerify.getBody().reason()).isEqualTo("missing_token");

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(tokens.refreshToken()), jsonHeaders()),
                AuthResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse rotatedTokens = refreshResponse.getBody();
        assertThat(rotatedTokens).isNotNull();
        assertThat(rotatedTokens.refreshToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(rotatedTokens.permissions()).containsExactly(expectedPermission);

        ResponseEntity<String> reusedRefreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(tokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(reusedRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> refreshAfterReuseDetectionResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(rotatedTokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(refreshAfterReuseDetectionResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<AuthResponse> secondLogin = restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(new LoginRequest(email, password), jsonHeaders()),
                AuthResponse.class);
        AuthResponse freshTokens = secondLogin.getBody();
        assertThat(freshTokens).isNotNull();

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(freshTokens.accessToken());
        logoutHeaders.set("API_ID", applicationId.toString());
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST,
                new HttpEntity<>(new LogoutRequest(freshTokens.refreshToken()), logoutHeaders), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<VerifyResponse> verifyAfterLogout = restTemplate.exchange(
                "/auth/verify", HttpMethod.POST, bearerEntity(freshTokens.accessToken()), VerifyResponse.class);
        assertThat(verifyAfterLogout.getBody().valid()).isFalse();
        assertThat(verifyAfterLogout.getBody().reason()).isEqualTo("revoked");

        ResponseEntity<String> refreshAfterLogoutResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(new RefreshRequest(freshTokens.refreshToken()), jsonHeaders()),
                String.class);
        assertThat(refreshAfterLogoutResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
