package com.darmoz.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Swagger UI en /auth/swagger-ui.html y el JSON en /auth/v3/api-docs (se apagan
 * con API_DOCS_ENABLED=false). La referencia completa sigue siendo API_GUIDE.md.
 */
@Configuration
public class OpenApiConfig {

    static final String API_ID = "apiId";
    static final String BEARER = "bearer";
    private static final String API_ID_HEADER = "API_ID";
    private static final Set<String> CREATED_METHODS = Set.of("register", "create");

    @Bean
    public OpenAPI darmozAuthOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("darmoz-auth API")
                        .version("v1")
                        .description("""
                                Autenticación centralizada de las aplicaciones de Darmoz. Guía completa: `API_GUIDE.md` \
                                del repo darmoz-auth.

                                **Credenciales** (botón Authorize):
                                - `API_ID`: UUID de la aplicación. Lo piden todos los endpoints de Auth.
                                - Bearer: el `accessToken` de `/login`, `/register` o `/refresh` (RS256, dura 12 minutos). \
                                El `refreshToken` dura 30 días y rota en cada `/refresh`: reusar uno viejo revoca todas \
                                las sesiones del usuario.

                                **Errores**: `{ timestamp, status, error, message }`. `error` es la frase HTTP salvo los \
                                códigos estables `EMAIL_NOT_VERIFIED` (403 en `/login`), `INVALID_CURRENT_PASSWORD` \
                                (400 en `/password`) y `TOO_MANY_REQUESTS` (429 al reenviar un código antes de 60 s)."""))
                // Relativo: Swagger UI lo resuelve contra el host desde el que se abre (Traefik o local).
                .servers(List.of(new Server().url("/auth")))
                .components(new Components()
                        .addSecuritySchemes(API_ID, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(API_ID_HEADER)
                                .description("UUID de la aplicación (tabla auth_applications)"))
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * Los headers API_ID y Authorization que los controllers reciben como @RequestHeader pasan a
     * ser esquemas de seguridad: se cargan una vez en Authorize (Swagger UI ignora un parámetro
     * llamado Authorization). /admin/api/** y /apps/api/** piden Bearer vía el filtro JWT.
     */
    @Bean
    public OperationCustomizer credentialsAsSecuritySchemes() {
        return (operation, handlerMethod) -> {
            boolean apiId = false;
            boolean bearer = isAdminController(handlerMethod.getBeanType());
            boolean bearerOptional = false;
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
                if (header != null && API_ID_HEADER.equals(headerName(header))) {
                    apiId = true;
                } else if (header != null && HttpHeaders.AUTHORIZATION.equalsIgnoreCase(headerName(header))) {
                    bearer = true;
                    bearerOptional = !header.required();
                } else if (parameter.hasParameterAnnotation(AuthenticationPrincipal.class)) {
                    bearer = true;
                }
            }
            if (operation.getParameters() != null) {
                List<Parameter> kept = new ArrayList<>(operation.getParameters());
                kept.removeIf(p -> "header".equals(p.getIn())
                        && (API_ID_HEADER.equals(p.getName()) || HttpHeaders.AUTHORIZATION.equalsIgnoreCase(p.getName())));
                operation.setParameters(kept.isEmpty() ? null : kept);
            }

            SecurityRequirement full = new SecurityRequirement();
            if (apiId) {
                full.addList(API_ID);
            }
            if (bearer) {
                full.addList(BEARER);
            }
            if (!full.isEmpty()) {
                operation.addSecurityItem(full);
            }
            if (bearer && bearerOptional && apiId) {
                operation.addSecurityItem(new SecurityRequirement().addList(API_ID));
            }
            fixSuccessStatus(operation, handlerMethod.getMethod());
            return operation;
        };
    }

    /**
     * Con ResponseEntity springdoc no ve el status real y documenta 200. Los controllers
     * responden noContent() en todos los ResponseEntity<Void> y 201 en register/create.
     */
    private static void fixSuccessStatus(Operation operation, Method method) {
        ApiResponses responses = operation.getResponses();
        if (responses == null || !responses.containsKey("200")
                || !ResponseEntity.class.equals(method.getReturnType())) {
            return;
        }
        boolean noBody = method.getGenericReturnType() instanceof ParameterizedType type
                && Void.class.equals(type.getActualTypeArguments()[0]);
        String status = noBody ? "204"
                : CREATED_METHODS.contains(method.getName()) ? "201" : null;
        if (status != null) {
            ApiResponse ok = responses.remove("200");
            responses.addApiResponse(status, noBody
                    ? new ApiResponse().description("No Content")
                    : ok.description("Created"));
        }
    }

    private static boolean isAdminController(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        for (String path : mapping.value().length > 0 ? mapping.value() : mapping.path()) {
            if (path.startsWith("/admin/api") || path.startsWith("/apps/api")) {
                return true;
            }
        }
        return false;
    }

    private static String headerName(RequestHeader header) {
        return !header.name().isEmpty() ? header.name() : header.value();
    }
}
