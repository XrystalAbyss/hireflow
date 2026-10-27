package com.hireflow.api.security;

import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Spring Security UserDetails adapter wrapping the certified domain {@link UserPrincipal}.
 * Password is intentionally excluded from the stored principal to eliminate credentials from session memory.
 */
public final class HireFlowUserPrincipal implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;

    private final UserPrincipal domainPrincipal;
    private final List<GrantedAuthority> authorities;

    public HireFlowUserPrincipal(UserPrincipal domainPrincipal) {
        this.domainPrincipal = Objects.requireNonNull(domainPrincipal, "domainPrincipal cannot be null");
        List<GrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + domainPrincipal.getRole().name()));
        if (domainPrincipal.getRole() == UserRole.ADMINISTRATOR) {
            auths.add(new SimpleGrantedAuthority("ROLE_PLACEMENT_OFFICER"));
        }
        this.authorities = Collections.unmodifiableList(auths);
    }

    public UserPrincipal getDomainPrincipal() {
        return domainPrincipal;
    }

    public String getDisplayName() {
        return domainPrincipal.getDisplayName();
    }

    public UserRole getRole() {
        return domainPrincipal.getRole();
    }

    public String getStudentId() {
        return domainPrincipal.getStudentId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return ""; // Zero retention of password hashes in active session
    }

    @Override
    public String getUsername() {
        return domainPrincipal.getName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HireFlowUserPrincipal that = (HireFlowUserPrincipal) o;
        return Objects.equals(domainPrincipal, that.domainPrincipal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domainPrincipal);
    }
}
