package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminCreateRolePermissionRequest(

        @NotNull UUID roleId,

        @NotBlank String service,

        @NotBlank String httpMethod,

        @NotBlank String endpointPattern
) {
}
