package com.darmoz.auth.config;

import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Crea el primer usuario SUPER si no existe todavia, a partir de
 * SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD. Idempotente: si ya existe un usuario con ese
 * email, no hace nada (no pisa password ni roles). Si las variables no estan seteadas,
 * solo loguea un warning y sigue - no bloquea el arranque de la aplicacion.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final String SUPER_ROLE = "SUPER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String superAdminEmail;
    private final String superAdminPassword;

    public SuperAdminBootstrap(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${SUPER_ADMIN_EMAIL:}") String superAdminEmail,
                                @Value("${SUPER_ADMIN_PASSWORD:}") String superAdminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.superAdminEmail = superAdminEmail;
        this.superAdminPassword = superAdminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (superAdminEmail.isBlank() || superAdminPassword.isBlank()) {
            log.warn("SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD no estan seteados; no se crea ningun usuario SUPER");
            return;
        }
        if (userRepository.existsByEmail(superAdminEmail)) {
            log.info("Usuario SUPER ya existe ({}), no se crea de nuevo", superAdminEmail);
            return;
        }

        Role superRole = roleRepository.findByName(SUPER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Rol '" + SUPER_ROLE + "' no existe; falta la migracion de seed"));
        User superUser = new User(superAdminEmail, passwordEncoder.encode(superAdminPassword), Set.of(superRole));
        userRepository.save(superUser);
        log.info("Usuario SUPER creado: {}", superAdminEmail);
    }
}
