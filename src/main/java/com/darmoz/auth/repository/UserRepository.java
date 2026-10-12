package com.darmoz.auth.repository;

import com.darmoz.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByApplicationIdAndEmail(UUID applicationId, String email);

    Optional<User> findByIdAndApplicationId(UUID id, UUID applicationId);

    /** Usuarios cuyo email empieza con el prefijo dado (StartingWith escapa los comodines LIKE). */
    List<User> findAllByApplicationIdAndEmailStartingWithOrderByCreatedAtAsc(UUID applicationId, String emailPrefix);

    List<User> findAllByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    boolean existsByApplicationIdAndEmail(UUID applicationId, String email);

    boolean existsByApplicationId(UUID applicationId);

    boolean existsByRoles_Id(UUID roleId);
}
