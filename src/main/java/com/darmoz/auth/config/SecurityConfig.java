package com.darmoz.auth.config;

import com.darmoz.auth.repository.RevokedAccessTokenRepository;
import com.darmoz.auth.security.AuthenticatedUser;
import com.darmoz.auth.security.JwtAuthenticationFilter;
import com.darmoz.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private static final String SUPER_AUTHORITY = "ROLE_SUPER";
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "API_ID");

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${cors.allowed-origins:https://darmozsc.duckdns.org}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                            RevokedAccessTokenRepository revokedAccessTokenRepository) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/register", "/login", "/refresh", "/logout", "/verify").permitAll()
                        // Prueban credenciales (email + password) en el body, sin Bearer: el usuario
                        // sin verificar no puede obtener un token (403 EMAIL_NOT_VERIFIED en /login).
                        .requestMatchers("/verify-email/confirm-with-credentials",
                                "/verify-email/resend-with-credentials").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/admin/api/**").access(systemSuperAdmin())
                        .requestMatchers("/admin/**").permitAll()
                        .requestMatchers("/apps/api/**").hasRole("APP_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, revokedAccessTokenRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // Sin esto, el LogoutFilter default de Spring Security intercepta POST /logout
                // (302 a /login?logout) y AuthController.logout nunca se ejecuta.
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * /admin/api/** exige rol SUPER **y** que el token sea de la aplicacion de sistema. Sin el
     * chequeo de aplicacion, un rol llamado SUPER en cualquier otra aplicacion (o un rol creado
     * por error) daria acceso global al panel de administracion.
     */
    private AuthorizationManager<RequestAuthorizationContext> systemSuperAdmin() {
        return (authenticationSupplier, context) -> {
            Authentication authentication = authenticationSupplier.get();
            boolean granted = authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedUser user
                    && SuperAdminBootstrap.SYSTEM_APPLICATION_ID.equals(user.applicationId())
                    && authentication.getAuthorities().stream()
                            .anyMatch(authority -> SUPER_AUTHORITY.equals(authority.getAuthority()));
            return new AuthorizationDecision(granted);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Origins configurables por env CORS_ALLOWED_ORIGINS (lista separada por comas), ver
        // cors.allowed-origins en application.yml.
        //
        // API pública (ver API_GUIDE.md sección 8): solo GET/POST/OPTIONS. Incluye /password y
        // /verify-email/*, que son todos POST.
        CorsConfiguration publicApiConfig = corsConfig(List.of("GET", "POST", "OPTIONS"));

        // Dashboard admin (/admin/api/**): necesita PUT/PATCH/DELETE (editar
        // roles, habilitar/deshabilitar, borrar). El browser manda header
        // Origin incluso en llamadas same-origin para métodos no-GET, así
        // que el filtro de CORS de Spring igual evalúa estos requests contra
        // la lista de métodos permitidos — si no está el método acá, lo
        // rechaza con 403 antes de llegar al controller.
        CorsConfiguration adminApiConfig = corsConfig(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Admin limitado a la aplicacion (/apps/api/**): alta, edicion parcial y baja de usuarios.
        CorsConfiguration appsApiConfig = corsConfig(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/admin/api/**", adminApiConfig);
        source.registerCorsConfiguration("/apps/api/**", appsApiConfig);
        source.registerCorsConfiguration("/**", publicApiConfig);
        return source;
    }

    private CorsConfiguration corsConfig(List<String> methods) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(methods);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(false);
        return config;
    }
}
