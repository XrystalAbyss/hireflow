package com.hireflow.web.security;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Result container for authentication attempts.
 * Returns either an authenticated {@link UserPrincipal} or a generic, sanitized failure reason.
 */
public final class AuthenticationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        SUCCESS,
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED,
        ACCOUNT_DISABLED,
        STUDENT_UNLINKED
    }

    private final Status status;
    private final UserPrincipal principal;
    private final String errorMessage;

    private AuthenticationResult(Status status, UserPrincipal principal, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.principal = principal;
        this.errorMessage = errorMessage;
    }

    public static AuthenticationResult success(UserPrincipal principal) {
        Objects.requireNonNull(principal, "principal cannot be null on SUCCESS");
        return new AuthenticationResult(Status.SUCCESS, principal, null);
    }

    public static AuthenticationResult failure(Status status, String errorMessage) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Cannot create failure with SUCCESS status");
        }
        return new AuthenticationResult(status, null, errorMessage != null ? errorMessage : "Invalid username or password.");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS && principal != null;
    }

    public Status getStatus() {
        return status;
    }

    public Optional<UserPrincipal> getPrincipal() {
        return Optional.ofNullable(principal);
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "Invalid username or password.";
    }
}
