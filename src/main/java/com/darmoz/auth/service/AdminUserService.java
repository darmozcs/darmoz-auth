package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminAssignRolesRequest;
import com.darmoz.auth.dto.request.AdminCreateUserRequest;
import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final UserAccountService userAccountService;

    public AdminUserService(UserRepository userRepository, ApplicationRepository applicationRepository,
                             UserAccountService userAccountService) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.userAccountService = userAccountService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return userRepository.findAll().stream().map(AdminUserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.of(findUserOrThrow(id));
    }

    @Transactional
    public AdminUserResponse create(AdminCreateUserRequest request) {
        Application application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new NotFoundException("Aplicacion no encontrada: " + request.applicationId()));
        // Creado por un admin (no self-registro publico via /register): se considera ya
        // verificado, si no quedaria potencialmente bloqueado por EMAIL_NOT_VERIFIED en su
        // primer /login.
        User user = userAccountService.createUser(application, request.email(), request.password(),
                userAccountService.resolveRoles(application.getId(), request.roles()), true);
        return AdminUserResponse.of(user);
    }

    /** Deshabilitar o cambiar la password revoca todos los refresh tokens del usuario. */
    @Transactional
    public AdminUserResponse update(UUID id, AdminUpdateUserRequest request) {
        User user = findUserOrThrow(id);
        return AdminUserResponse.of(userAccountService.update(user, request.enabled(), request.password()));
    }

    @Transactional
    public AdminUserResponse replaceRoles(UUID id, AdminAssignRolesRequest request) {
        User user = findUserOrThrow(id);
        // Roles se resuelven scoped a la aplicacion DEL USUARIO, no elegible por el caller —
        // consistente con no permitir reasignar la aplicacion de un usuario existente.
        user.setRoles(userAccountService.resolveRoles(user.getApplication().getId(), request.roles()));
        return AdminUserResponse.of(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        userAccountService.delete(findUserOrThrow(id));
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
    }
}
