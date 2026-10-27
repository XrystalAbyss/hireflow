package com.hireflow.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CsrfTokenManager - Server-side CSRF Token Validation Tests")
class CsrfTokenManagerTest {

    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
    }

    @Test
    @DisplayName("generation: generates and caches 64-hex random token in session")
    void testGetOrCreateToken() {
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(null);

        String token = CsrfTokenManager.getOrCreateToken(session);

        assertNotNull(token);
        assertEquals(64, token.length(), "Token must be 32 bytes encoded in 64 hex characters");
        verify(session).setAttribute(eq(CsrfTokenManager.CSRF_SESSION_ATTR), eq(token));
    }

    @Test
    @DisplayName("validation: passes when valid token matches in request parameter")
    void testValidateTokenFromParameter() {
        String testToken = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(testToken);
        when(request.getParameter(CsrfTokenManager.CSRF_PARAM_NAME)).thenReturn(testToken);

        assertTrue(CsrfTokenManager.validateToken(request));
    }

    @Test
    @DisplayName("validation: passes when valid token matches in request header")
    void testValidateTokenFromHeader() {
        String testToken = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(testToken);
        when(request.getHeader(CsrfTokenManager.CSRF_HEADER_NAME)).thenReturn(testToken);

        assertTrue(CsrfTokenManager.validateToken(request));
    }

    @Test
    @DisplayName("validation: fails when token is missing or mismatched")
    void testRejectMismatchedOrMissingToken() {
        String testToken = "1111111111111111111111111111111111111111111111111111111111111111";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(testToken);

        // Missing
        when(request.getParameter(CsrfTokenManager.CSRF_PARAM_NAME)).thenReturn(null);
        when(request.getHeader(CsrfTokenManager.CSRF_HEADER_NAME)).thenReturn(null);
        assertFalse(CsrfTokenManager.validateToken(request));

        // Mismatched
        when(request.getParameter(CsrfTokenManager.CSRF_PARAM_NAME)).thenReturn("wrong_token_value");
        assertFalse(CsrfTokenManager.validateToken(request));
    }

    @Test
    @DisplayName("validation: fails when session is null")
    void testRejectNullSession() {
        when(request.getSession(false)).thenReturn(null);
        assertFalse(CsrfTokenManager.validateToken(request));
    }

    @Test
    @DisplayName("rotation: rotateToken generates a fresh token and updates session attribute")
    void testRotateToken() {
        String oldToken = "oldtoken1234567890abcdef1234567890abcdef1234567890abcdef12345678";
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(oldToken);

        String newToken = CsrfTokenManager.rotateToken(session);

        assertNotNull(newToken);
        assertEquals(64, newToken.length());
        assertNotEquals(oldToken, newToken);
        verify(session).setAttribute(eq(CsrfTokenManager.CSRF_SESSION_ATTR), eq(newToken));
    }
}
