package com.ghasl_service.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds a Content-Security-Policy-Report-Only header for monitoring ONLY.
 *
 * IMPORTANT: 'unsafe-inline' in script-src and style-src gives NO XSS protection.
 * This header is monitoring-only: it reports what would be blocked if enforcement
 * were later introduced. Enable only to observe; do NOT change to enforced mode
 * without first auditing and removing all inline scripts and styles.
 *
 * CDN hosts detected across all HTML pages (dashboard.html, pos.html, settings.html,
 * report.html, index.html):
 *   https://cdn.tailwindcss.com        - Tailwind CSS
 *   https://cdn.jsdelivr.net           - Chart.js
 *   https://cdnjs.cloudflare.com       - Font Awesome
 *   https://fonts.googleapis.com       - Google Fonts CSS
 *   https://fonts.gstatic.com          - Google Fonts files
 *   https://*.tile.openstreetmap.org   - Leaflet map tiles
 *
 * Enable via application.properties: app.csp.report-only.enabled=true (default: false)
 * This filter ONLY adds a response header; it does not change authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "app.csp.report-only.enabled", havingValue = "true")
public class CspReportOnlyFilter extends OncePerRequestFilter {

    // NOTE: 'unsafe-inline' means NO XSS protection -- monitoring only.
    private static final String CSP_POLICY =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://cdn.jsdelivr.net; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
            "img-src 'self' data: https://*.tile.openstreetmap.org; " +
            "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
            "connect-src 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy-Report-Only", CSP_POLICY);
        filterChain.doFilter(request, response);
    }
}
