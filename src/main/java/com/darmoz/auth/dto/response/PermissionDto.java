package com.darmoz.auth.dto.response;

public record PermissionDto(String service, String method, String path) {
}
