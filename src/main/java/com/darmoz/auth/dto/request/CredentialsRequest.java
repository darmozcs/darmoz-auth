package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body de /verify-email/resend-with-credentials. */
public record CredentialsRequest(

        @NotBlank String email,

        @NotBlank String password
) {
}
