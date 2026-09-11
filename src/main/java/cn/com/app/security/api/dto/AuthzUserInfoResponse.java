package cn.com.app.security.api.dto;

import java.util.Collection;

public record AuthzUserInfoResponse(
        Long id,
        String username,
        String displayName,
        String issuer,
        Collection<String> roles,
        Collection<String> authorities
) {
}
