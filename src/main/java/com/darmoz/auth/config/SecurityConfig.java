package com.darmoz.auth.config;

import com.darmoz.auth.repository.RevokedAccessTokenRepository;
import com.darmoz.auth.security.JwtAuthenticationFilter;
import com.darmoz.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                            RevokedAccessTokenRepository revokedAccessTokenRepository) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/register", "/login", "/refresh", "/logout", "/verify").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/admin/api/**").hasRole("SUPER")
                        .requestMatchers("/admin/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, revokedAccessTokenRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // API pública (ver API_GUIDE.md sección 7): solo GET/POST/OPTIONS.
        CorsConfiguration publicApiConfig = new CorsConfiguration();
        publicApiConfig.setAllowedOrigins(List.of("https://darmozsc.duckdns.org"));
        publicApiConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicApiConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        publicApiConfig.setAllowCredentials(false);

        // Dashboard admin (/admin/api/**): necesita PUT/PATCH/DELETE (editar
        // roles, habilitar/deshabilitar, borrar). El browser manda header
        // Origin incluso en llamadas same-origin para métodos no-GET, así
        // que el filtro de CORS de Spring igual evalúa estos requests contra
        // la lista de métodos permitidos — si no está el método acá, lo
        // rechaza con 403 antes de llegar al controller.
        CorsConfiguration adminApiConfig = new CorsConfiguration();
        adminApiConfig.setAllowedOrigins(List.of("https://darmozsc.duckdns.org"));
        adminApiConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        adminApiConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        adminApiConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/admin/api/**", adminApiConfig);
        source.registerCorsConfiguration("/**", publicApiConfig);
        return source;
    }
}
