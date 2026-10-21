package com.darmoz.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** El Swagger es lo que se le entrega a los fronts: público y con API_ID/Bearer donde corresponde. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode apiDocs() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    @Test
    void apiDocsArePublicAndListTheEndpoints() throws Exception {
        JsonNode docs = apiDocs();
        // Copia para entregar sin levantar el servicio (no se versiona: target/).
        Files.writeString(Path.of("target", "openapi.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docs));

        assertThat(docs.get("paths").has("/login")).isTrue();
        assertThat(docs.get("paths").has("/apps/api/users/{id}/verification-email")).isTrue();
        assertThat(docs.get("servers").get(0).get("url").asText()).isEqualTo("/auth");
    }

    @Test
    void headersBecomeSecuritySchemes() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        JsonNode login = paths.get("/login").get("post");
        assertThat(login.get("security").toString()).isEqualTo("[{\"apiId\":[]}]");
        assertThat(login.has("parameters")).isFalse();

        JsonNode password = paths.get("/password").get("post");
        assertThat(password.get("security").toString()).isEqualTo("[{\"apiId\":[],\"bearer\":[]}]");

        // Bearer opcional: con o sin token.
        JsonNode logout = paths.get("/logout").get("post");
        assertThat(logout.get("security")).hasSize(2);

        assertThat(paths.get("/register").get("post").get("responses").has("201")).isTrue();
        assertThat(logout.get("responses").has("204")).isTrue();
        assertThat(logout.get("responses").has("200")).isFalse();

        JsonNode adminUsers = paths.get("/admin/api/users").get("get");
        assertThat(adminUsers.get("security").toString()).isEqualTo("[{\"bearer\":[]}]");
    }

    @Test
    void swaggerUiIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
