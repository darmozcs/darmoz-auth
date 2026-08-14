package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record AdminCreateUserRequest(

        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 100) String password,

        @NotNull UUID applicationId,

        @NotEmpty Set<String> roles
) {
}
