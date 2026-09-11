package cn.com.app.security.api.dto;

import java.util.List;

public record RoleResponse(
        Long id,
        String code,
        String name,
        List<PermissionResponse> permissions
) {
}
