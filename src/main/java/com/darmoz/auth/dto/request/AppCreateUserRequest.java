package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Alta de usuario via /apps/api/users: la aplicacion sale del token del caller, no del body. */
public record AppCreateUserRequest(

        @NotBlank @Email String email,

        @NotBlank @Size(min = 8, max = 100) String password,

        @NotEmpty Set<String> roles
) {
}
