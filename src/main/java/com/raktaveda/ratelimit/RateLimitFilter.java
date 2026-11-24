package com.raktaveda.ratelimit;

import com.raktaveda.common.ErrorResponseWriter;
import com.raktaveda.config.RaktavedaProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how many requests one IP address can make per minute, with a stricter limit for
 * login/OTP URLs and for search. Later stages add per-user limits (requests, chat).
 * Not a Spring bean on purpose: SecurityConfig creates it, so it runs only once.
 *
 * The IP comes from request.getRemoteAddr(). Behind a reverse proxy, set
 * RAKTAVEDA_FORWARD_HEADERS=native so that this is the real client address.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final RaktavedaProperties props;
    private final ErrorResponseWriter writer;

    public RateLimitFilter(RateLimiter limiter, RaktavedaProperties props, ErrorResponseWriter writer) {
        this.limiter = limiter;
        this.props = props;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String bucket;
        int limit;
        if (path.startsWith("/api/auth/")) {
            bucket = "auth";
            limit = props.rateLimit().authPerMinute();
        } else if (path.startsWith("/api/search")) {
            bucket = "search";
            limit = props.rateLimit().searchPerMinute();
        } else {
            bucket = "general";
            limit = props.rateLimit().generalPerMinute();
        }
        if (!limiter.tryAcquire(bucket + ":" + request.getRemoteAddr(), limit, WINDOW)) {
            response.setHeader("Retry-After", "60");
            writer.write(response, 429, "RATE_LIMITED", "error.rate_limited");
            return;
        }
        chain.doFilter(request, response);
    }
}
