package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 80) String displayName
) {
}
