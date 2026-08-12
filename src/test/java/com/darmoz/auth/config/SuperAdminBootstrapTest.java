package com.darmoz.auth.config;

import com.darmoz.auth.entity.Role;
import com.darmoz.auth.repository.RoleRepository;
import com.darmoz.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void doesNothingWhenEnvVarsAreBlank() {
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(userRepository, roleRepository, passwordEncoder, "", "");

        bootstrap.run(null);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenSuperUserAlreadyExists() {
        when(userRepository.existsByEmail("super@darmoz.com")).thenReturn(true);
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(
                userRepository, roleRepository, passwordEncoder, "super@darmoz.com", "Super1234!");

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsSuperUserWhenMissing() {
        when(userRepository.existsByEmail("super@darmoz.com")).thenReturn(false);
        when(roleRepository.findByName("SUPER")).thenReturn(Optional.of(new Role("SUPER")));
        when(passwordEncoder.encode("Super1234!")).thenReturn("hashed");
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(
                userRepository, roleRepository, passwordEncoder, "super@darmoz.com", "Super1234!");

        bootstrap.run(null);

        verify(userRepository, times(1)).save(any());
    }

    @Test
    void runningTwiceOnlyCreatesOnce() {
        when(roleRepository.findByName("SUPER")).thenReturn(Optional.of(new Role("SUPER")));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(
                userRepository, roleRepository, passwordEncoder, "super@darmoz.com", "Super1234!");

        when(userRepository.existsByEmail("super@darmoz.com")).thenReturn(false);
        bootstrap.run(null);

        when(userRepository.existsByEmail("super@darmoz.com")).thenReturn(true);
        bootstrap.run(null);

        verify(userRepository, times(1)).save(any());
    }
}
