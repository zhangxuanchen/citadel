package cn.com.app.security.api.dto;

import java.time.Instant;

public record SsoTicketResponse(
        String ticket,
        String appCode,
        String redirectUri,
        String state,
        Instant expiresAt,
        String redirectUrl
) {
}
