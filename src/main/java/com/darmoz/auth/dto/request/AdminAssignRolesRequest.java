package com.darmoz.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AdminAssignRolesRequest(

        @NotEmpty Set<String> roles
) {
}
