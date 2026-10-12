package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.request.AppCreateUserRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.ApplicationMismatchException;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.UserRepository;
import com.darmoz.auth.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administracion de usuarios limitada a una aplicacion (/apps/api/users, rol APP_ADMIN). La
 * aplicacion SIEMPRE sale del access token del caller, nunca del request: un usuario de otra
 * aplicacion es indistinguible de uno inexistente (404).
 */
@Service
public class AppUserService {

    private static final String APP_ADMIN_ROLE = "APP_ADMIN";
    private static final String SUPER_ROLE = "SUPER";
    /** Roles que un APP_ADMIN no puede asignar (evita escalada de privilegios). */
    private static final Set<String> RESERVED_ROLES = Set.of(APP_ADMIN_ROLE, SUPER_ROLE);

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final UserAccountService userAccountService;
    private final EmailVerificationService emailVerificationService;

    public AppUserService(UserRepository userRepository, ApplicationRepository applicationRepository,
                           UserAccountService userAccountService, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.userAccountService = userAccountService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(AuthenticatedUser caller) {
        return userRepository.findAllByApplicationIdOrderByCreatedAtAsc(requireApplicationId(caller)).stream()
                .map(AdminUserResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(AuthenticatedUser caller, UUID id) {
        return AdminUserResponse.of(findScopedUserOrThrow(caller, id));
    }

    /**
     * Crea el usuario con email_verified=false y le envia el codigo de verificacion en la misma
     * transaccion: si darmoz-mail falla (502) se hace rollback y el usuario no queda creado.
     */
    @Transactional
    public AdminUserResponse create(AuthenticatedUser caller, AppCreateUserRequest request) {
        UUID applicationId = requireApplicationId(caller);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Aplicacion no encontrada"));

        request.roles().stream()
                .filter(RESERVED_ROLES::contains)
                .findFirst()
                .ifPresent(role -> {
                    throw new ConflictException("No se puede asignar el rol " + role);
                });

        Set<Role> roles = userAccountService.resolveRoles(applicationId, request.roles());
        User user = userAccountService.createUser(application, request.email(), request.password(), roles, false);
        emailVerificationService.generateAndSend(user);
        return AdminUserResponse.of(user);
    }

    /** Deshabilitar o cambiar la password revoca todos los refresh tokens del usuario. */
    @Transactional
    public AdminUserResponse update(AuthenticatedUser caller, UUID id, AdminUpdateUserRequest request) {
        User user = findModifiableUserOrThrow(caller, id);
        return AdminUserResponse.of(userAccountService.update(user, request.enabled(), request.password()));
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id) {
        userAccountService.delete(findModifiableUserOrThrow(caller, id));
    }

    @Transactional
    public void resendVerificationEmail(AuthenticatedUser caller, UUID id) {
        emailVerificationService.generateAndSend(findScopedUserOrThrow(caller, id));
    }

    private UUID requireApplicationId(AuthenticatedUser caller) {
        if (caller.applicationId() == null) {
            throw new ApplicationMismatchException("El access token no tiene una aplicacion asociada");
        }
        return caller.applicationId();
    }

    private User findScopedUserOrThrow(AuthenticatedUser caller, UUID id) {
        return userRepository.findByIdAndApplicationId(id, requireApplicationId(caller))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    /** Ni el propio usuario ni otro APP_ADMIN de la aplicacion se pueden modificar/borrar. */
    private User findModifiableUserOrThrow(AuthenticatedUser caller, UUID id) {
        if (id.equals(caller.userId())) {
            throw new ConflictException("No podes modificar ni eliminar tu propio usuario");
        }
        User user = findScopedUserOrThrow(caller, id);
        boolean isAppAdmin = user.getRoles().stream().anyMatch(role -> APP_ADMIN_ROLE.equals(role.getName()));
        if (isAppAdmin) {
            throw new ConflictException("No se puede modificar ni eliminar a otro administrador de la aplicacion");
        }
        return user;
    }
}
