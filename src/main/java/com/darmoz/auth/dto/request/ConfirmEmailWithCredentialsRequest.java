package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body de /verify-email/confirm-with-credentials. */
public record ConfirmEmailWithCredentialsRequest(

        @NotBlank String email,

        @NotBlank String password,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "el codigo debe tener 6 digitos")
        String code
) {
}
