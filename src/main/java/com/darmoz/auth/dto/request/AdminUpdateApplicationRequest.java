package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.Min;

/** Update parcial: los campos ausentes (null) no se modifican. */
public record AdminUpdateApplicationRequest(

        String serviceName,

        String name,

        String description,

        @Min(0) Integer unverifiedLoginLimit
) {
}
