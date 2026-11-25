package com.arrheniq.kernel.security;

/**
 * Accepts only same-origin relative paths as post-login / post-re-authentication targets,
 * preventing open redirects (ASVS 5.1.5).
 */
public final class SafeRedirects {

    private SafeRedirects() { }

    public static String orDefault(String candidate, String fallback) {
        return isSafe(candidate) ? candidate : fallback;
    }

    public static boolean isSafe(String path) {
        if (path == null || path.isEmpty() || path.length() > 2048) {
            return false;
        }
        if (path.charAt(0) != '/' || path.startsWith("//") || path.startsWith("/\\")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '\\') {
                return false;
            }
        }
        return true;
    }
}
