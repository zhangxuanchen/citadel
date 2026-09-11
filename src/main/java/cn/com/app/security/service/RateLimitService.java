package cn.com.app.security.service;

import cn.com.app.security.config.SecurityProtectionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final SecurityProtectionProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimitService(SecurityProtectionProperties properties) {
        this.properties = properties;
    }

    public RateLimitResult consume(String key) {
        SecurityProtectionProperties.RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            return RateLimitResult.allowed(0);
        }

        Instant now = Instant.now();
        cleanupExpiredBuckets(now, config.getWindow());
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));

        synchronized (bucket) {
            if (!now.isBefore(bucket.windowStartedAt.plus(config.getWindow()))) {
                bucket.windowStartedAt = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count <= config.getMaxRequests()) {
                return RateLimitResult.allowed(0);
            }
            long retryAfterSeconds = Math.max(1, Duration.between(now, bucket.windowStartedAt.plus(config.getWindow())).toSeconds());
            return RateLimitResult.blocked(retryAfterSeconds);
        }
    }

    private void cleanupExpiredBuckets(Instant now, Duration window) {
        if (requestCounter.incrementAndGet() % 1000 != 0) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (!now.isBefore(entry.getValue().windowStartedAt.plus(window))) {
                iterator.remove();
            }
        }
    }

    private static class Bucket {
        private Instant windowStartedAt;
        private int count;

        private Bucket(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

        public static RateLimitResult allowed(long retryAfterSeconds) {
            return new RateLimitResult(true, retryAfterSeconds);
        }

        public static RateLimitResult blocked(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds);
        }
    }
}
