package com.hireflow.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.mockito.Mockito.*;

@DisplayName("SecurityHeadersFilter - HTTP Response Security Headers Tests")
class SecurityHeadersFilterTest {

    @Test
    @DisplayName("doFilter: sets strict security headers and UTF-8 encoding on response")
    void testSecurityHeadersAreApplied() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(resp).setHeader("X-Content-Type-Options", "nosniff");
        verify(resp).setHeader("X-Frame-Options", "DENY");
        verify(resp).setHeader("X-XSS-Protection", "0");
        verify(resp).setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        verify(resp).setHeader(eq("Content-Security-Policy"), anyString());
        verify(req).setCharacterEncoding("UTF-8");
        verify(resp).setCharacterEncoding("UTF-8");
        verify(chain).doFilter(req, resp);
    }

    @Test
    @DisplayName("doFilter: blocks non-HTTPS remote access outside localhost with HTTP 403")
    void testRejectsRemoteNonHttps() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getRemoteAddr()).thenReturn("203.0.113.195");
        when(req.getServerName()).thenReturn("hireflow.example.edu");
        when(req.isSecure()).thenReturn(false);

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("HTTPS is mandatory"));
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("doFilter: allows remote HTTPS and adds HSTS header")
    void testAllowsRemoteHttpsWithHsts() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getRemoteAddr()).thenReturn("203.0.113.195");
        when(req.getServerName()).thenReturn("hireflow.example.edu");
        when(req.isSecure()).thenReturn(true);

        filter.doFilter(req, resp, chain);

        verify(resp).setHeader(eq("Strict-Transport-Security"), contains("max-age=31536000"));
        verify(chain).doFilter(req, resp);
    }

    @Test
    @DisplayName("doFilter: untrusted remote client cannot bypass HTTPS by spoofing X-Forwarded-Proto")
    void testUntrustedClientCannotSpoofForwardedProto() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getRemoteAddr()).thenReturn("203.0.113.195");
        when(req.getServerName()).thenReturn("hireflow.example.edu");
        when(req.isSecure()).thenReturn(false);
        when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("HTTPS is mandatory"));
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("doFilter: explicitly configured trusted reverse proxy X-Forwarded-Proto is accepted")
    void testTrustedReverseProxyForwardedProtoAccepted() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        filter.setTrustedProxies(Set.of("10.0.0.1", "10.0.0.2"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getRemoteAddr()).thenReturn("10.0.0.1");
        when(req.getServerName()).thenReturn("hireflow.example.edu");
        when(req.isSecure()).thenReturn(false);
        when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");

        filter.doFilter(req, resp, chain);

        verify(resp).setHeader(eq("Strict-Transport-Security"), contains("max-age=31536000"));
        verify(chain).doFilter(req, resp);
    }
}
