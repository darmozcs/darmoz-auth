package com.darmoz.auth.service;

import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.exception.UserAlreadyExistsException;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logica de gestion de cuentas compartida entre el admin global (/admin/api/users,
 * AdminUserService), el admin limitado a una aplicacion (/apps/api/users, AppUserService) y el
 * cambio de password propio (/password). No hace chequeos de autorizacion: eso es
 * responsabilidad de cada caller.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserAccountService(UserRepository userRepository, RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /** Resuelve nombres de rol dentro de la aplicacion indicada (404 si alguno no existe ahi). */
    public Set<Role> resolveRoles(UUID applicationId, Set<String> names) {
        return names.stream()
                .map(name -> roleRepository.findByApplicationIdAndName(applicationId, name)
                        .orElseThrow(() -> new NotFoundException("Rol no encontrado: " + name)))
                .collect(Collectors.toSet());
    }

    @Transactional
    public User createUser(Application application, String email, String rawPassword, Set<Role> roles,
                           boolean emailVerified) {
        if (userRepository.existsByApplicationIdAndEmail(application.getId(), email)) {
            throw new UserAlreadyExistsException("Ya existe una cuenta con ese email");
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), roles, application);
        user.setEmailVerified(emailVerified);
        return userRepository.save(user);
    }

    /**
     * Update parcial (null = no se modifica). Deshabilitar o cambiar la password revoca todos
     * los refresh tokens del usuario: ninguna sesion puede sobrevivir a esos cambios.
     */
    @Transactional
    public User update(User user, Boolean enabled, String rawPassword) {
        boolean revokeSessions = false;
        if (enabled != null) {
            if (!enabled) {
                revokeSessions = true;
            }
            user.setEnabled(enabled);
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            revokeSessions = true;
        }
        User saved = userRepository.save(user);
        if (revokeSessions) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        return saved;
    }

    public boolean passwordMatches(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void changePassword(User user, String rawPassword) {
        update(user, null, rawPassword);
    }

    @Transactional
    public void delete(User user) {
        refreshTokenService.revokeAllForUser(user.getId());
        userRepository.delete(user);
    }
}
