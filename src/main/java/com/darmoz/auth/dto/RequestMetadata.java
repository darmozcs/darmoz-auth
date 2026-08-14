package com.darmoz.auth.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

public record RequestMetadata(String origin, String host, String userAgent, String referer) {

    public static RequestMetadata from(HttpServletRequest request) {
        return new RequestMetadata(
                request.getHeader(HttpHeaders.ORIGIN),
                request.getHeader(HttpHeaders.HOST),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getHeader(HttpHeaders.REFERER));
    }
}
