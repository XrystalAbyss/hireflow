package com.hireflow.api.dto;

import java.io.Serializable;

/**
 * Public DTO exposing Cross-Site Request Forgery (CSRF) protection metadata.
 */
public record CsrfTokenDto(
        String token,
        String headerName,
        String parameterName
) implements Serializable {}
