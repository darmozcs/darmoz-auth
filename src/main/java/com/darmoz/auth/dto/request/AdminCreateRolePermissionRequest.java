package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminCreateRolePermissionRequest(

        @NotBlank String role,

        @NotBlank String service,

        @NotBlank String httpMethod,

        @NotBlank String endpointPattern
) {
}
