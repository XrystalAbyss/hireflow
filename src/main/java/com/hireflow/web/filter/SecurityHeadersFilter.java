package com.hireflow.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global HTTP response security filter for HireFlow Web Portal.
 * <p>
 * Enforces standard security headers:
 * <ul>
 *     <li>X-Content-Type-Options: nosniff (prevents MIME sniffing)</li>
 *     <li>X-Frame-Options: DENY (clickjacking protection)</li>
 *     <li>X-XSS-Protection: 0 (disables legacy buggy XSS filters)</li>
 *     <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 *     <li>Content-Security-Policy: restrict source origins for assets and styles</li>
 *     <li>HTTPS enforcement outside localhost development environments</li>
 *     <li>Explicit trusted reverse-proxy verification before accepting X-Forwarded-Proto</li>
 *     <li>UTF-8 Character Encoding</li>
 * </ul>
 */
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {

    public static final String TRUSTED_PROXIES_PROPERTY = "hireflow.trusted.proxy.ips";
    public static final String TRUSTED_PROXIES_INIT_PARAM = "trustedProxyIps";

    private Set<String> trustedProxies = Collections.emptySet();

    @Override
    public void init(FilterConfig filterConfig) {
        String sysProp = System.getProperty(TRUSTED_PROXIES_PROPERTY);
        String initParam = filterConfig != null ? filterConfig.getInitParameter(TRUSTED_PROXIES_INIT_PARAM) : null;
        String configStr = (sysProp != null && !sysProp.trim().isEmpty()) ? sysProp : initParam;
        if (configStr != null && !configStr.trim().isEmpty()) {
            this.trustedProxies = Arrays.stream(configStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public void setTrustedProxies(Set<String> proxies) {
        this.trustedProxies = proxies != null ? Set.copyOf(proxies) : Collections.emptySet();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 1. Enforce HTTPS outside localhost development environments
        String remoteAddr = httpReq.getRemoteAddr();
        String serverName = httpReq.getServerName();
        boolean isLoopback = remoteAddr == null
                || "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(serverName);

        // Security requirement: Only trust X-Forwarded-Proto if supplied by an explicitly configured trusted proxy
        boolean isBehindTrustedProxy = remoteAddr != null && trustedProxies.contains(remoteAddr.trim());
        String forwardedProto = isBehindTrustedProxy ? httpReq.getHeader("X-Forwarded-Proto") : null;
        boolean isSecure = httpReq.isSecure() || "https".equalsIgnoreCase(forwardedProto);

        if (!isLoopback && !isSecure) {
            httpResp.sendError(HttpServletResponse.SC_FORBIDDEN, "HTTPS is mandatory for remote access outside localhost.");
            return;
        }

        if (isSecure) {
            httpResp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        // 2. Attach defense-in-depth HTTP security headers
        httpResp.setHeader("X-Content-Type-Options", "nosniff");
        httpResp.setHeader("X-Frame-Options", "DENY");
        httpResp.setHeader("X-XSS-Protection", "0");
        httpResp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResp.setHeader("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                        "font-src 'self' https://fonts.gstatic.com; img-src 'self' data:;");

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
