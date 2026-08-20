package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailVerificationRequest(

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "el codigo debe tener 6 digitos")
        String code
) {
}
