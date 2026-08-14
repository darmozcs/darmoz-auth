package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AdminCreateRoleRequest(

        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "debe ser MAYUSCULAS, empezar con letra, solo letras/numeros/guion bajo")
        String name,

        String description,

        @NotNull UUID applicationId
) {
}
