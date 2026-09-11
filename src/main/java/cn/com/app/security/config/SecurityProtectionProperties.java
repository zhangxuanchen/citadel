package cn.com.app.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "authz.security")
public class SecurityProtectionProperties {

    private final RateLimit rateLimit = new RateLimit();
    private final LoginProtection loginProtection = new LoginProtection();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public LoginProtection getLoginProtection() {
        return loginProtection;
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int maxRequests = 120;
        private Duration window = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class LoginProtection {
        private boolean enabled = true;
        private int maxFailures = 5;
        private Duration lockDuration = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public Duration getLockDuration() {
            return lockDuration;
        }

        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }
    }
}
