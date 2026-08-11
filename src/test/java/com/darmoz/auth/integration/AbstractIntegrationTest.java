package com.darmoz.auth.integration;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path PRIVATE_KEY_PATH;
    static final Path PUBLIC_KEY_PATH;

    static {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            Path tempDir = Files.createTempDirectory("darmoz-auth-test-keys");
            PRIVATE_KEY_PATH = tempDir.resolve("private_key.pem");
            PUBLIC_KEY_PATH = tempDir.resolve("public_key.pem");
            Files.writeString(PRIVATE_KEY_PATH, toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(PUBLIC_KEY_PATH, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("No se pudieron generar las llaves RSA de test", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.private-key-path", () -> PRIVATE_KEY_PATH.toString());
        registry.add("jwt.public-key-path", () -> PUBLIC_KEY_PATH.toString());
    }

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
