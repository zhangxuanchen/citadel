package cn.com.app.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String issuer,
        String keyId,
        String privateKey,
        String publicKey,
        Duration expiration,
        Duration refreshExpiration
) {
}
