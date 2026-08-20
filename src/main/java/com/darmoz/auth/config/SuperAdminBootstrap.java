package com.darmoz.auth.config;

import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.repository.ApplicationRepository;
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
import java.util.UUID;

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

    /** Id fijo sembrado por V5__add_applications.sql; igual en todo entorno. */
    public static final UUID SYSTEM_APPLICATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final String superAdminEmail;
    private final String superAdminPassword;

    public SuperAdminBootstrap(UserRepository userRepository,
                                RoleRepository roleRepository,
                                ApplicationRepository applicationRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${SUPER_ADMIN_EMAIL:}") String superAdminEmail,
                                @Value("${SUPER_ADMIN_PASSWORD:}") String superAdminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationRepository = applicationRepository;
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

        Application systemApplication = applicationRepository.findById(SYSTEM_APPLICATION_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Aplicacion de sistema (" + SYSTEM_APPLICATION_ID + ") no existe; falta la migracion de seed"));

        if (userRepository.existsByApplicationIdAndEmail(systemApplication.getId(), superAdminEmail)) {
            log.info("Usuario SUPER ya existe ({}), no se crea de nuevo", superAdminEmail);
            return;
        }

        Role superRole = roleRepository.findByApplicationIdAndName(systemApplication.getId(), SUPER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Rol '" + SUPER_ROLE + "' no existe; falta la migracion de seed"));
        User superUser = new User(superAdminEmail, passwordEncoder.encode(superAdminPassword), Set.of(superRole), systemApplication);
        // Cuenta de bootstrap creada a partir de variables de entorno del propio operador,
        // no de un self-registro publico: se considera ya verificada, si no quedaria
        // bloqueada por EMAIL_NOT_VERIFIED en el primer /login (unverified_login_limit
        // default es 0).
        superUser.setEmailVerified(true);
        userRepository.save(superUser);
        log.info("Usuario SUPER creado: {}", superAdminEmail);
    }
}
