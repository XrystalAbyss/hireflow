package com.hireflow.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Server-side Cross-Site Request Forgery (CSRF) token manager.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Generates cryptographically random 256-bit tokens using {@link SecureRandom}.</li>
 *     <li>Binds token to the server-side {@link HttpSession}.</li>
 *     <li>Tokens are validated strictly via request payload (parameter or header), never via GET query strings.</li>
 *     <li>Constant-time comparison via {@link MessageDigest#isEqual(byte[], byte[])} prevents timing attacks.</li>
 *     <li>Token values are never written to logs or URL parameters.</li>
 * </ul>
 */
public final class CsrfTokenManager {

    public static final String CSRF_SESSION_ATTR = "hireflow.csrfToken";
    public static final String CSRF_PARAM_NAME = "csrfToken";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CsrfTokenManager() {
    }

    /**
     * Retrieves the existing CSRF token for the session, or creates a new one if absent.
     *
     * @param session Active HTTP session.
     * @return Hex-encoded 64-character token.
     */
    public static String getOrCreateToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("HttpSession cannot be null to manage CSRF tokens");
        }
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF_SESSION_ATTR);
            if (token == null || token.trim().isEmpty()) {
                token = generateRandomToken();
                session.setAttribute(CSRF_SESSION_ATTR, token);
            }
            return token;
        }
    }

    /**
     * Generates and unconditionally sets a brand-new CSRF token in the session.
     * Used upon session rotation during login or sensitive state transitions.
     *
     * @param session Active HTTP session.
     * @return Freshly generated 64-character Hex token.
     */
    public static String rotateToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("HttpSession cannot be null to rotate CSRF tokens");
        }
        synchronized (session) {
            String token = generateRandomToken();
            session.setAttribute(CSRF_SESSION_ATTR, token);
            return token;
        }
    }

    /**
     * Validates whether the request contains a valid CSRF token matching the session token.
     * Checks header 'X-CSRF-Token', parameter 'csrfToken', and parameter '_csrf'.
     *
     * @param request Incoming HTTP request.
     * @return true if token is present, non-empty, and matches in constant time; false otherwise.
     */
    public static boolean validateToken(HttpServletRequest request) {
        if (request == null) return false;
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        // Check header first, then form parameters
        String candidate = request.getHeader(CSRF_HEADER_NAME);
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = request.getParameter(CSRF_PARAM_NAME);
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = request.getParameter("_csrf");
        }

        return isValidToken(session, candidate);
    }

    /**
     * Validates candidate token against session token using constant-time comparison.
     */
    public static boolean isValidToken(HttpSession session, String candidate) {
        if (session == null || candidate == null || candidate.trim().isEmpty()) {
            return false;
        }
        String sessionToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }
        return constantTimeEquals(candidate.trim(), sessionToken.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private static String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
