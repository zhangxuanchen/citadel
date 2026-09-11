package cn.com.app.security.api.dto;

import java.util.Collection;

public record LoginResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String issuer,
        String username,
        Collection<String> authorities
) {
}
