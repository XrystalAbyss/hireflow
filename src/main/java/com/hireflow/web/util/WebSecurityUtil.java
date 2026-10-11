package com.hireflow.web.util;

/**
 * Web security utilities for input sanitization, HTML escaping, and path safety.
 */
public final class WebSecurityUtil {

    private WebSecurityUtil() {
    }

    /**
     * Escapes untrusted text for safe insertion into HTML bodies or attributes.
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.max(16, input.length()));
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#x27;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Validates that a requested view or resource path does not attempt directory traversal.
     */
    public static boolean isSafeInternalPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return !path.contains("..") && !path.contains("//") && !path.contains("\\") && path.startsWith("/");
    }
}
