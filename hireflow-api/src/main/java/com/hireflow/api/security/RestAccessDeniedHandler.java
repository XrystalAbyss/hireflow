package com.hireflow.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Access denied handler returning standard sanitized JSON error envelopes
 * for authorization and CSRF validation failures.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        String path = request.getRequestURI();
        String message = (accessDeniedException instanceof CsrfException)
                ? "Invalid or missing CSRF security token."
                : "Access is denied: insufficient role privileges.";

        String json = "{\"status\":403," +
                "\"error\":\"Forbidden\"," +
                "\"message\":\"" + sanitizeJson(message) + "\"," +
                "\"path\":\"" + sanitizeJson(path) + "\"," +
                "\"timestamp\":\"" + Instant.now() + "\"}";

        response.getWriter().write(json);
    }

    private String sanitizeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
