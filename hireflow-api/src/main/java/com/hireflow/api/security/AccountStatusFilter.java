package com.hireflow.api.security;

import com.hireflow.web.security.UserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Security filter enforcing continuous session validity, immediate account revocation,
 * and role synchronization on every authenticated HTTP request.
 * <p>
 * If an active account is disabled, locked, deleted, or has its role modified in PostgreSQL,
 * the authenticated session is immediately invalidated and rejected with 401 Unauthorized.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final ApiAuthenticationAdapter authenticationAdapter;

    public AccountStatusFilter(ApiAuthenticationAdapter authenticationAdapter) {
        this.authenticationAdapter = Objects.requireNonNull(authenticationAdapter, "authenticationAdapter cannot be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof HireFlowUserPrincipal principal) {
            Optional<UserAccount> accountOpt = authenticationAdapter.findAccount(principal.getUsername());

            boolean isRevoked = false;
            String message = null;

            if (accountOpt.isEmpty()) {
                isRevoked = true;
                message = "Account no longer exists. Session terminated.";
            } else {
                UserAccount account = accountOpt.get();
                if (!account.isEnabled()) {
                    isRevoked = true;
                    message = "Account is disabled. Session terminated.";
                } else if (account.isAccountLocked(Instant.now())) {
                    isRevoked = true;
                    message = "Account is locked due to security policy. Session terminated.";
                } else if (account.getRole() != principal.getRole()) {
                    isRevoked = true;
                    message = "Account role privileges have changed. Please log in again.";
                }
            }

            if (isRevoked) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();

                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
                response.setHeader(HttpHeaders.PRAGMA, "no-cache");

                String path = request.getRequestURI();
                String json = "{\"status\":401," +
                        "\"error\":\"Unauthorized\"," +
                        "\"message\":\"" + sanitizeJson(message) + "\"," +
                        "\"path\":\"" + sanitizeJson(path) + "\"," +
                        "\"timestamp\":\"" + Instant.now() + "\"}";

                response.getWriter().write(json);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String sanitizeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
