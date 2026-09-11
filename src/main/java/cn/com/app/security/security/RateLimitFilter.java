package cn.com.app.security.security;

import cn.com.app.security.logging.OperationLogAspect;
import cn.com.app.security.service.AuditLogService;
import cn.com.app.security.service.RateLimitService;
import cn.com.app.security.service.RateLimitService.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;

    public RateLimitFilter(RateLimitService rateLimitService, AuditLogService auditLogService) {
        this.rateLimitService = rateLimitService;
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = clientIp(request);
        RateLimitResult result = rateLimitService.consume(clientIp);
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("rate_limit_blocked clientIp={} method={} uri={} retryAfterSeconds={}",
                clientIp, request.getMethod(), request.getRequestURI(), result.retryAfterSeconds());
        request.setAttribute(OperationLogAspect.AUDIT_RECORDED_ATTRIBUTE, true);
        auditLogService.record(
                "security",
                "rate_limit_blocked",
                "ip",
                clientIp,
                "method=%s,uri=%s,retryAfterSeconds=%s".formatted(
                        request.getMethod(),
                        request.getRequestURI(),
                        result.retryAfterSeconds()
                )
        );
        writeTooManyRequests(response, result.retryAfterSeconds());
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Too many requests, please try again later"}
                """.formatted(Instant.now()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
