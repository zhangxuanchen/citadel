package cn.com.app.security.api.dto;

public record LogoutRequest(
        String refreshToken
) {
}
