package cn.com.app.security.api.dto;

public record PermissionResponse(
        Long id,
        String code,
        String name,
        String appCode,
        String appName
) {
}
