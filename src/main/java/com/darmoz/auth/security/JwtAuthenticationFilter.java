package com.darmoz.auth.security;

import com.darmoz.auth.repository.RevokedAccessTokenRepository;
import com.darmoz.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica requests a partir de un access token JWT (Authorization: Bearer ...).
 * No rechaza requests con token ausente/invalido: solo deja el SecurityContext vacio y
 * es authorizeHttpRequests() el que decide si la ruta requiere autenticacion.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    public JwtAuthenticationFilter(JwtService jwtService, RevokedAccessTokenRepository revokedAccessTokenRepository) {
        this.jwtService = jwtService;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.ParsedAccessToken parsed = jwtService.parse(token);
                if (!revokedAccessTokenRepository.existsByJti(parsed.jti())) {
                    List<GrantedAuthority> authorities = parsed.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .map(GrantedAuthority.class::cast)
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(parsed.userId(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException ignored) {
                // token invalido/expirado/malformado: se sigue sin autenticacion
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
