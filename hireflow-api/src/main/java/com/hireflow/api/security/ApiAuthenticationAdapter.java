package com.hireflow.api.security;

import com.hireflow.web.security.AuthenticationResult;
import com.hireflow.web.security.AuthenticationService;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
import com.hireflow.web.security.UserPrincipal;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter integrating legacy {@link AuthenticationService} and {@link UserAccountRepository}
 * into the Spring Boot REST API layer.
 */
@Component
public class ApiAuthenticationAdapter implements UserDetailsService {

    private final AuthenticationService authenticationService;
    private final UserAccountRepository userAccountRepository;

    public ApiAuthenticationAdapter(
            AuthenticationService authenticationService,
            UserAccountRepository userAccountRepository) {
        this.authenticationService = Objects.requireNonNull(authenticationService, "authenticationService cannot be null");
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository, "userAccountRepository cannot be null");
    }

    /**
     * Authenticates candidate credentials using the certified BCrypt and brute-force protection logic.
     * Plaintext password characters are wiped from memory immediately upon completion.
     */
    public AuthenticationResult authenticate(String username, String password, String clientIp) {
        char[] passwordChars = (password != null) ? password.toCharArray() : new char[0];
        try {
            return authenticationService.authenticate(username, passwordChars, clientIp);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    /**
     * Re-verifies if an account is currently enabled and not locked out.
     * Enforces continuous session validity for active authenticated sessions.
     */
    public boolean isAccountActive(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        try {
            String normalized = UserAccount.normalizeUsername(username);
            Optional<UserAccount> optAccount = userAccountRepository.findByUsername(normalized);
            if (optAccount.isEmpty()) {
                return false;
            }
            UserAccount account = optAccount.get();
            return account.isEnabled() && !account.isAccountLocked(Instant.now());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Retrieves the persistent account to obtain refreshed metadata such as lastLoginAt.
     */
    public Optional<UserAccount> findAccount(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String normalized = UserAccount.normalizeUsername(username);
            return userAccountRepository.findByUsername(normalized);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Username cannot be empty");
        }
        String normalized = UserAccount.normalizeUsername(username);
        UserAccount account = userAccountRepository.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));
        if (!account.isEnabled()) {
            throw new DisabledException("Account is disabled: " + username);
        }
        if (account.isAccountLocked(Instant.now())) {
            throw new LockedException("Account is temporarily locked: " + username);
        }
        UserPrincipal domainPrincipal = new UserPrincipal(
                account.getUsername(),
                account.getDisplayName(),
                account.getRole(),
                account.getStudentId()
        );
        return new HireFlowUserPrincipal(domainPrincipal);
    }
}
