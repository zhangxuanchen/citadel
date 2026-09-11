package cn.com.app.security.service;

import cn.com.app.security.config.SecurityProtectionProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final SecurityProtectionProperties properties;
    private final AuditLogService auditLogService;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(SecurityProtectionProperties properties, AuditLogService auditLogService) {
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    public void assertNotLocked(String username) {
        if (!properties.getLoginProtection().isEnabled()) {
            return;
        }
        String key = key(username, clientIp());
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil == null) {
            return;
        }
        Instant now = Instant.now();
        if (state.lockedUntil.isAfter(now)) {
            long retryAfterSeconds = Math.max(1, Duration.between(now, state.lockedUntil).toSeconds());
            log.warn("login_blocked reason=too_many_failures username={} clientIp={} retryAfterSeconds={}",
                    username, clientIp(), retryAfterSeconds);
            auditLogService.record(
                    "security",
                    "login_blocked",
                    "user",
                    username,
                    "clientIp=%s,retryAfterSeconds=%s".formatted(clientIp(), retryAfterSeconds)
            );
            throw new ResponseStatusException(HttpStatus.LOCKED, "Login temporarily locked, please try again later");
        }
        attempts.remove(key);
    }

    public void recordFailure(String username, String reason) {
        if (!properties.getLoginProtection().isEnabled()) {
            return;
        }
        cleanupExpiredAttempts();
        String clientIp = clientIp();
        String key = key(username, clientIp);
        SecurityProtectionProperties.LoginProtection config = properties.getLoginProtection();
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState());

        synchronized (state) {
            state.failureCount++;
            if (state.failureCount >= config.getMaxFailures()) {
                state.lockedUntil = Instant.now().plus(config.getLockDuration());
                log.warn("login_locked username={} clientIp={} failures={} reason={} lockedUntil={}",
                        username, clientIp, state.failureCount, reason, state.lockedUntil);
                auditLogService.record(
                        "security",
                        "login_locked",
                        "user",
                        username,
                        "clientIp=%s,failures=%s,reason=%s,lockedUntil=%s".formatted(
                                clientIp,
                                state.failureCount,
                                reason,
                                state.lockedUntil
                        )
                );
                return;
            }
            log.warn("login_failed username={} clientIp={} failures={} reason={}",
                    username, clientIp, state.failureCount, reason);
        }
    }

    public void recordSuccess(String username) {
        if (!properties.getLoginProtection().isEnabled()) {
            return;
        }
        attempts.remove(key(username, clientIp()));
    }

    private void cleanupExpiredAttempts() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, AttemptState>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            AttemptState state = iterator.next().getValue();
            if (state.lockedUntil != null && !state.lockedUntil.isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private String key(String username, String clientIp) {
        String safeUsername = username == null ? "unknown" : username.trim().toLowerCase(Locale.ROOT);
        return safeUsername + "|" + clientIp;
    }

    private String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "N/A";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static class AttemptState {
        private int failureCount;
        private Instant lockedUntil;
    }
}
