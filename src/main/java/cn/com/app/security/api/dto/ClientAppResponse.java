package cn.com.app.security.api.dto;

public record ClientAppResponse(
        Long id,
        String code,
        String name,
        String description,
        Boolean enabled
) {
}
