package com.darmoz.auth.dto.request;

/** Update parcial: los campos ausentes (null) no se modifican. */
public record AdminUpdateApplicationRequest(

        String serviceName,

        String name,

        String description
) {
}
