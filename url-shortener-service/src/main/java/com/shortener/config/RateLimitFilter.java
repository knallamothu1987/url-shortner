package com.shortener.config;

import com.shortener.application.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-user (falls back to per-IP) rate limiting on URL creation. Registered inside the
 * security filter chain after authorization so the JWT principal is available.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final boolean enabled;

    public RateLimitFilter(RedisRateLimiter rateLimiter,
                           @Value("${url-shortener.rate-limit.enabled:true}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !"/api/v1/urls".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (rateLimiter.tryConsume(clientKey(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"URL creation rate limit exceeded; retry later"}""");
    }

    private String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null) {
            return authentication.getName();
        }
        return request.getRemoteAddr();
    }
}
