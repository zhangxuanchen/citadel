package cn.com.app.security.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaptchaService {

    private static final Duration EXPIRE_AFTER = Duration.ofMinutes(5);

    private final Map<String, CaptchaItem> captchas = new ConcurrentHashMap<>();

    public String store(String code) {
        cleanupExpired();
        String captchaId = UUID.randomUUID().toString();
        captchas.put(captchaId, new CaptchaItem(normalize(code), Instant.now().plus(EXPIRE_AFTER)));
        return captchaId;
    }

    public void validate(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Captcha is required");
        }

        CaptchaItem item = captchas.remove(captchaId);
        if (item == null || item.expired() || !item.code().equals(normalize(captchaCode))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid captcha");
        }
    }

    private void cleanupExpired() {
        captchas.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private record CaptchaItem(String code, Instant expireAt) {

        boolean expired() {
            return Instant.now().isAfter(expireAt);
        }
    }
}
