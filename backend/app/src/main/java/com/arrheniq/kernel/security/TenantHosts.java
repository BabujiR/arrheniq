package com.arrheniq.kernel.security;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Derives the tenant slug from the request host (multi-tenancy.md §2): {@code acme.arrheniq.com}
 * with base domain {@code arrheniq.com} gives {@code acme}. Only one label is accepted, so
 * {@code a.b.arrheniq.com} and look-alike domains such as {@code acme.arrheniq.com.evil.io} give nothing.
 */
public final class TenantHosts {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,40}$");

    private TenantHosts() { }

    public static Optional<String> slugFromHost(String host, String baseDomain) {
        if (host == null || baseDomain == null || baseDomain.isBlank()) {
            return Optional.empty();
        }
        String h = stripPort(host.trim().toLowerCase(Locale.ROOT));
        String base = baseDomain.trim().toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        String suffix = "." + base;
        if (!h.endsWith(suffix)) {
            return Optional.empty();
        }
        String label = h.substring(0, h.length() - suffix.length());
        if (label.contains(".") || !SLUG.matcher(label).matches()) {
            return Optional.empty();
        }
        return Optional.of(label);
    }

    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            return host;   // IPv6 literal: never a tenant host
        }
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }
}
