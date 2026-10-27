package com.hireflow.api.controller;

import com.hireflow.api.dto.ApiErrorResponse;
import com.hireflow.api.dto.CsrfTokenDto;
import com.hireflow.api.dto.LoginRequestDto;
import com.hireflow.api.dto.LogoutResponseDto;
import com.hireflow.api.dto.UserProfileDto;
import com.hireflow.api.security.ApiAuthenticationAdapter;
import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.web.security.AuthenticationResult;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * REST controller managing secure session authentication, CSRF delivery, identity verification,
 * and session invalidation.
 * <p>
 * Key Security Controls:
 * <ul>
 *     <li>Zero password hashes serialized or logged.</li>
 *     <li>CSRF validation strictly enforced on state-changing endpoints (/login and /logout).</li>
 *     <li>Session fixation protection via {@link SessionAuthenticationStrategy} session rotation.</li>
 *     <li>Continuous account validity check against PostgreSQL on identity resolution.</li>
 *     <li>Anti-enumeration: Uniform failure messages for invalid usernames and wrong passwords.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ApiAuthenticationAdapter authenticationAdapter;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(
            ApiAuthenticationAdapter authenticationAdapter,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationAdapter = Objects.requireNonNull(authenticationAdapter, "authenticationAdapter cannot be null");
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository, "securityContextRepository cannot be null");
        this.sessionAuthenticationStrategy = Objects.requireNonNull(sessionAuthenticationStrategy, "sessionAuthenticationStrategy cannot be null");
        this.csrfTokenRepository = Objects.requireNonNull(csrfTokenRepository, "csrfTokenRepository cannot be null");
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenDto> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken == null && csrfTokenRepository != null) {
            csrfToken = csrfTokenRepository.loadDeferredToken(request, response).get();
        }

        if (csrfToken == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        CsrfTokenDto dto = new CsrfTokenDto(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(dto);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDto loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = request.getRemoteAddr();
        AuthenticationResult result = authenticationAdapter.authenticate(
                loginRequest.username(),
                loginRequest.password(),
                clientIp
        );

        if (!result.isSuccess() || result.getPrincipal().isEmpty()) {
            HttpStatus status = (result.getStatus() == AuthenticationResult.Status.ACCOUNT_LOCKED)
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : (result.getStatus() == AuthenticationResult.Status.ACCOUNT_DISABLED)
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;

            ApiErrorResponse error = ApiErrorResponse.of(
                    status.value(),
                    status.getReasonPhrase(),
                    result.getErrorMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(status)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(error);
        }

        UserPrincipal domainPrincipal = result.getPrincipal().get();
        HireFlowUserPrincipal springPrincipal = new HireFlowUserPrincipal(domainPrincipal);

        // 1. Establish Spring Security Authentication Token & Context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                springPrincipal,
                null,
                springPrincipal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 2. Execute Spring Security Session Fixation & CSRF Rotation Strategy
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        // 3. Persist context into the newly rotated session
        securityContextRepository.saveContext(context, request, response);

        Optional<UserAccount> accountOpt = authenticationAdapter.findAccount(domainPrincipal.getName());
        Instant lastLoginAt = accountOpt.map(UserAccount::getLastLoginAt).orElse(null);

        UserProfileDto profile = new UserProfileDto(
                domainPrincipal.getName(),
                domainPrincipal.getDisplayName(),
                domainPrincipal.getRole().name(),
                domainPrincipal.getStudentId(),
                lastLoginAt
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(profile);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof HireFlowUserPrincipal principal)) {
            ApiErrorResponse error = ApiErrorResponse.of(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Full authentication is required to access this resource.",
                    request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(error);
        }

        // Continuous session validity: re-check account active status
        if (!authenticationAdapter.isAccountActive(principal.getUsername())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            ApiErrorResponse error = ApiErrorResponse.of(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Account is disabled or locked. Session has been terminated.",
                    request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(error);
        }

        Optional<UserAccount> accountOpt = authenticationAdapter.findAccount(principal.getUsername());
        Instant lastLoginAt = accountOpt.map(UserAccount::getLastLoginAt).orElse(null);

        UserProfileDto profile = new UserProfileDto(
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRole().name(),
                principal.getStudentId(),
                lastLoginAt
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDto> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        if (csrfTokenRepository != null) {
            csrfTokenRepository.saveToken(null, request, response);
        }

        String cookiePath = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        Cookie killCookie = new Cookie("JSESSIONID", "");
        killCookie.setPath(cookiePath);
        killCookie.setMaxAge(0);
        killCookie.setHttpOnly(true);
        killCookie.setAttribute("SameSite", "Lax");
        if (request.isSecure()) {
            killCookie.setSecure(true);
        }
        response.addCookie(killCookie);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(new LogoutResponseDto("Logged out successfully."));
    }
}
