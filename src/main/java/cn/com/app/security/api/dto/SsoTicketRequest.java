package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SsoTicketRequest(
        @NotBlank @Size(max = 60) String appCode,
        @NotBlank @Size(max = 500) String redirectUri,
        @Size(max = 120) String state
) {
}
