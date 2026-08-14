package com.darmoz.auth.integration;

import com.darmoz.auth.config.SuperAdminBootstrap;
import com.darmoz.auth.dto.request.AdminAssignRolesRequest;
import com.darmoz.auth.dto.request.AdminCreateRolePermissionRequest;
import com.darmoz.auth.dto.request.AdminCreateRoleRequest;
import com.darmoz.auth.dto.request.AdminCreateUserRequest;
import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.request.LoginRequest;
import com.darmoz.auth.dto.request.RegisterRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.dto.response.AuthResponse;
import com.darmoz.auth.dto.response.RolePermissionResponse;
import com.darmoz.auth.dto.response.RoleResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminControllerIT extends AbstractIntegrationTest {

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

    private String superToken;
    private Application laryonApplication;

    @BeforeEach
    void seedSuperUser() {
        Application systemApplication = applicationRepository.findById(SuperAdminBootstrap.SYSTEM_APPLICATION_ID).orElseThrow();
        laryonApplication = applicationRepository.findByName("Laryon").orElseThrow();

        Role superRole = roleRepository.findByApplicationIdAndName(systemApplication.getId(), "SUPER").orElseThrow();
        String email = "super-" + UUID.randomUUID() + "@darmoz.com";
        String password = "Super1234!";
        userRepository.save(new User(email, passwordEncoder.encode(password), Set.of(superRole), systemApplication));

        ResponseEntity<AuthResponse> login = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password), apiIdHeaders(systemApplication.getId())),
                AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        superToken = login.getBody().accessToken();
    }

    @Test
    void nonSuperUserGetsForbidden() {
        String email = "plain-" + UUID.randomUUID() + "@darmoz.com";
        restTemplate.exchange("/auth/register", HttpMethod.POST,
                new HttpEntity<>(new RegisterRequest(email, "Plain1234!"), apiIdHeaders(laryonApplication.getId())),
                AuthResponse.class);
        ResponseEntity<AuthResponse> login = restTemplate.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, "Plain1234!"), apiIdHeaders(laryonApplication.getId())),
                AuthResponse.class);
        String plainToken = login.getBody().accessToken();

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/admin/api/users", HttpMethod.GET, bearerEntity(plainToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requestWithoutTokenGetsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity("/auth/admin/api/users", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dashboardStaticAssetsAreServedWithoutAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/auth/admin/index.html", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Panel de administraci");
    }

    @Test
    void fullAdminCrudLifecycle() {
        // Roles: crear, listar
        ResponseEntity<RoleResponse> createRole = restTemplate.exchange(
                "/auth/admin/api/roles", HttpMethod.POST,
                new HttpEntity<>(new AdminCreateRoleRequest("BILLING", "Acceso a facturacion", laryonApplication.getId()), authHeaders()),
                RoleResponse.class);
        assertThat(createRole.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createRole.getBody().name()).isEqualTo("BILLING");

        ResponseEntity<RoleResponse[]> listRoles = restTemplate.exchange(
                "/auth/admin/api/roles", HttpMethod.GET, bearerEntity(superToken), RoleResponse[].class);
        assertThat(listRoles.getBody()).extracting(RoleResponse::name).contains("USER", "ADMIN", "SUPER", "BILLING");

        // Role-permissions: crear para el rol nuevo
        ResponseEntity<RolePermissionResponse> createPermission = restTemplate.exchange(
                "/auth/admin/api/role-permissions", HttpMethod.POST,
                new HttpEntity<>(new AdminCreateRolePermissionRequest(createRole.getBody().id(), "nexora-api", "GET", "/api/invoices/**"), authHeaders()),
                RolePermissionResponse.class);
        assertThat(createPermission.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Duplicado -> 409
        ResponseEntity<String> duplicatePermission = restTemplate.exchange(
                "/auth/admin/api/role-permissions", HttpMethod.POST,
                new HttpEntity<>(new AdminCreateRolePermissionRequest(createRole.getBody().id(), "nexora-api", "GET", "/api/invoices/**"), authHeaders()),
                String.class);
        assertThat(duplicatePermission.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Usuarios: crear con el rol nuevo
        ResponseEntity<AdminUserResponse> createUser = restTemplate.exchange(
                "/auth/admin/api/users", HttpMethod.POST,
                new HttpEntity<>(new AdminCreateUserRequest("billing-" + UUID.randomUUID() + "@darmoz.com", "Billing1234!",
                        laryonApplication.getId(), Set.of("BILLING")), authHeaders()),
                AdminUserResponse.class);
        assertThat(createUser.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createUser.getBody().roles()).containsExactly("BILLING");
        UUID userId = createUser.getBody().id();

        // Deshabilitar
        ResponseEntity<AdminUserResponse> disableUser = restTemplate.exchange(
                "/auth/admin/api/users/" + userId, HttpMethod.PATCH,
                new HttpEntity<>(new AdminUpdateUserRequest(false, null), authHeaders()),
                AdminUserResponse.class);
        assertThat(disableUser.getBody().enabled()).isFalse();

        // Reasignar roles a USER
        ResponseEntity<AdminUserResponse> reassign = restTemplate.exchange(
                "/auth/admin/api/users/" + userId + "/roles", HttpMethod.PUT,
                new HttpEntity<>(new AdminAssignRolesRequest(Set.of("USER")), authHeaders()),
                AdminUserResponse.class);
        assertThat(reassign.getBody().roles()).containsExactly("USER");

        // Borrar el rol BILLING falla porque el permission todavia existe
        ResponseEntity<String> deleteRoleInUse = restTemplate.exchange(
                "/auth/admin/api/roles/" + createRole.getBody().id(), HttpMethod.DELETE,
                bearerEntity(superToken), String.class);
        assertThat(deleteRoleInUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Borrar el permiso, despues si se puede borrar el rol
        restTemplate.exchange("/auth/admin/api/role-permissions/" + createPermission.getBody().id(),
                HttpMethod.DELETE, bearerEntity(superToken), Void.class);
        ResponseEntity<Void> deleteRole = restTemplate.exchange(
                "/auth/admin/api/roles/" + createRole.getBody().id(), HttpMethod.DELETE,
                bearerEntity(superToken), Void.class);
        assertThat(deleteRole.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Borrar el usuario
        ResponseEntity<Void> deleteUser = restTemplate.exchange(
                "/auth/admin/api/users/" + userId, HttpMethod.DELETE, bearerEntity(superToken), Void.class);
        assertThat(deleteUser.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getDeletedUser = restTemplate.exchange(
                "/auth/admin/api/users/" + userId, HttpMethod.GET, bearerEntity(superToken), String.class);
        assertThat(getDeletedUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<Void> bearerEntity(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(superToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders apiIdHeaders(UUID applicationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API_ID", applicationId.toString());
        return headers;
    }
}
