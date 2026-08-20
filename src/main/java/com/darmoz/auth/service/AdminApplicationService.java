package com.darmoz.auth.service;

import com.darmoz.auth.dto.request.AdminCreateApplicationRequest;
import com.darmoz.auth.dto.request.AdminUpdateApplicationRequest;
import com.darmoz.auth.dto.response.ApplicationResponse;
import com.darmoz.auth.entity.Application;
import com.darmoz.auth.entity.Role;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.NotFoundException;
import com.darmoz.auth.repository.ApplicationRepository;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminApplicationService {

    private static final String DEFAULT_ROLE = "USER";
    private static final String DEFAULT_ROLE_DESCRIPTION = "Rol base asignado a todo usuario registrado";

    private final ApplicationRepository applicationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public AdminApplicationService(ApplicationRepository applicationRepository,
                                    RoleRepository roleRepository,
                                    UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list() {
        return applicationRepository.findAll().stream().map(ApplicationResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id) {
        return ApplicationResponse.of(findApplicationOrThrow(id));
    }

    /**
     * Crea la aplicacion y, en la misma transaccion, su rol base USER — sin esto,
     * /register para esta aplicacion nueva fallaria siempre con "rol base no existe".
     */
    @Transactional
    public ApplicationResponse create(AdminCreateApplicationRequest request) {
        if (applicationRepository.existsByName(request.name())) {
            throw new ConflictException("Ya existe una aplicacion con ese nombre");
        }
        Application application = new Application(request.serviceName(), request.name(), request.description());
        application.setUnverifiedLoginLimit(request.unverifiedLoginLimit() == null ? 0 : request.unverifiedLoginLimit());
        applicationRepository.save(application);
        roleRepository.save(new Role(DEFAULT_ROLE, DEFAULT_ROLE_DESCRIPTION, application));
        return ApplicationResponse.of(application);
    }

    @Transactional
    public ApplicationResponse update(UUID id, AdminUpdateApplicationRequest request) {
        Application application = findApplicationOrThrow(id);
        if (request.name() != null && !request.name().isBlank()) {
            if (applicationRepository.existsByNameAndIdNot(request.name(), id)) {
                throw new ConflictException("Ya existe una aplicacion con ese nombre");
            }
            application.setName(request.name());
        }
        if (request.serviceName() != null && !request.serviceName().isBlank()) {
            application.setServiceName(request.serviceName());
        }
        if (request.description() != null) {
            application.setDescription(request.description());
        }
        if (request.unverifiedLoginLimit() != null) {
            application.setUnverifiedLoginLimit(request.unverifiedLoginLimit());
        }
        return ApplicationResponse.of(applicationRepository.save(application));
    }

    @Transactional
    public void delete(UUID id) {
        if (!applicationRepository.existsById(id)) {
            throw new NotFoundException("Aplicacion no encontrada: " + id);
        }
        if (userRepository.existsByApplicationId(id)) {
            throw new ConflictException("La aplicacion tiene usuarios asociados; no se puede borrar");
        }
        if (roleRepository.existsByApplicationId(id)) {
            throw new ConflictException("La aplicacion tiene roles asociados; no se puede borrar");
        }
        applicationRepository.deleteById(id);
    }

    private Application findApplicationOrThrow(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Aplicacion no encontrada: " + id));
    }
}
