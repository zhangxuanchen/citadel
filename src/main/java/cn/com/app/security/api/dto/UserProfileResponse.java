package cn.com.app.security.api.dto;

import java.util.Collection;

public record UserProfileResponse(
        Long id,
        String username,
        String displayName,
        Boolean enabled,
        Collection<String> roles,
        Collection<String> permissions
) {
}
