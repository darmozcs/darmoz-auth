package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.Size;

/** Update parcial: los campos ausentes (null) no se modifican. */
public record AdminUpdateUserRequest(

        Boolean enabled,

        @Size(min = 8, max = 100) String password
) {
}
