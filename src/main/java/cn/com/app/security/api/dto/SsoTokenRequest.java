package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoTokenRequest(
        @NotBlank String ticket
) {
}
