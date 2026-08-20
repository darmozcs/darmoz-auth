package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminCreateApplicationRequest(

        @NotBlank String serviceName,

        @NotBlank String name,

        String description,

        @Min(0) Integer unverifiedLoginLimit
) {
}
