package com.darmoz.auth.service;

import com.darmoz.auth.config.JwtProperties;
import com.darmoz.auth.dto.response.PermissionDto;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties properties;
    private User user;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        properties = new JwtProperties();
        properties.setAccessTokenTtlSeconds(720);
        properties.setIssuer("darmoz-auth");

        jwtService = new JwtService(keyPair.getPrivate(), keyPair.getPublic(), properties);
        user = new User(UUID.randomUUID(), "user@darmoz.com", "hash", Set.of(new Role("USER"), new Role("ADMIN")));
    }

    @Test
    void generatesTokenWithExpectedClaims() {
        List<PermissionDto> permissions = List.of(
                new PermissionDto("nexora-api", "GET", "/api/products/**"),
                new PermissionDto("nexora-api", "POST", "/api/orders"));
        JwtService.IssuedAccessToken issued = jwtService.generateAccessToken(user, permissions);

        JwtService.ParsedAccessToken parsed = jwtService.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo(user.getEmail());
        assertThat(parsed.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(parsed.permissions()).containsExactlyInAnyOrderElementsOf(permissions);
        assertThat(parsed.jti()).isEqualTo(issued.jti());
    }

    @Test
    void generatesTokenWithNoPermissions() {
        JwtService.IssuedAccessToken issued = jwtService.generateAccessToken(user, List.of());

        JwtService.ParsedAccessToken parsed = jwtService.parse(issued.token());

        assertThat(parsed.permissions()).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        properties.setAccessTokenTtlSeconds(-10);
        JwtService.IssuedAccessToken issued = jwtService.generateAccessToken(user, List.of());

        assertThatThrownBy(() -> jwtService.parse(issued.token()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() throws NoSuchAlgorithmException {
        JwtService.IssuedAccessToken issued = jwtService.generateAccessToken(user, List.of());

        KeyPair otherKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        JwtService jwtServiceWithOtherKey = new JwtService(otherKeyPair.getPrivate(), otherKeyPair.getPublic(), properties);

        assertThatThrownBy(() -> jwtServiceWithOtherKey.parse(issued.token()))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
