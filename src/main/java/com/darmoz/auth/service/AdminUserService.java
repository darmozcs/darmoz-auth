package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminAssignRolesRequest;
import com.darmoz.auth.dto.request.AdminCreateUserRequest;
import com.darmoz.auth.dto.request.AdminUpdateUserRequest;
import com.darmoz.auth.dto.response.AdminUserResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.exception.UserAlreadyExistsException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, RoleRepository roleRepository,
                             ApplicationRepository applicationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (userRepository.existsByApplicationIdAndEmail(application.getId(), request.email())) {
            throw new UserAlreadyExistsException("Ya existe una cuenta con ese email");
        }
        Set<Role> roles = resolveRoles(application.getId(), request.roles());
        User user = new User(request.email(), passwordEncoder.encode(request.password()), roles, application);
        return AdminUserResponse.of(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse update(UUID id, AdminUpdateUserRequest request) {
        User user = findUserOrThrow(id);
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return AdminUserResponse.of(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse replaceRoles(UUID id, AdminAssignRolesRequest request) {
        User user = findUserOrThrow(id);
        // Roles se resuelven scoped a la aplicacion DEL USUARIO, no elegible por el caller —
        // consistente con no permitir reasignar la aplicacion de un usuario existente.
        user.setRoles(resolveRoles(user.getApplication().getId(), request.roles()));
        return AdminUserResponse.of(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Usuario no encontrado: " + id);
        }
        userRepository.deleteById(id);
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
    }

    private Set<Role> resolveRoles(UUID applicationId, Set<String> names) {
        return names.stream()
                .map(name -> roleRepository.findByApplicationIdAndName(applicationId, name)
                        .orElseThrow(() -> new NotFoundException("Rol no encontrado: " + name)))
                .collect(Collectors.toSet());
    }
}
