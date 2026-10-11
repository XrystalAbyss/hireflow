package com.hireflow.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSecurityUtil - Security Sanitization & Path Safety Tests")
class WebSecurityUtilTest {

    @Test
    @DisplayName("escapeHtml: properly escapes HTML injection characters")
    void testEscapeHtml() {
        assertEquals("", WebSecurityUtil.escapeHtml(null));
        assertEquals("Hello World", WebSecurityUtil.escapeHtml("Hello World"));
        assertEquals("&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;",
                WebSecurityUtil.escapeHtml("<script>alert('XSS')</script>"));
        assertEquals("Tom &amp; Jerry &quot;Quotes&quot;",
                WebSecurityUtil.escapeHtml("Tom & Jerry \"Quotes\""));
    }

    @Test
    @DisplayName("isSafeInternalPath: accepts valid internal paths and rejects directory traversal")
    void testSafeInternalPath() {
        assertTrue(WebSecurityUtil.isSafeInternalPath("/WEB-INF/views/home.jsp"));
        assertTrue(WebSecurityUtil.isSafeInternalPath("/static/css/style.css"));

        assertFalse(WebSecurityUtil.isSafeInternalPath(null));
        assertFalse(WebSecurityUtil.isSafeInternalPath(""));
        assertFalse(WebSecurityUtil.isSafeInternalPath("home.jsp")); // Missing leading slash
        assertFalse(WebSecurityUtil.isSafeInternalPath("/views/../etc/passwd")); // Directory traversal
        assertFalse(WebSecurityUtil.isSafeInternalPath("//evil.com/redirect")); // Double slash
        assertFalse(WebSecurityUtil.isSafeInternalPath("/views\\windows\\path")); // Backslash
    }
}
