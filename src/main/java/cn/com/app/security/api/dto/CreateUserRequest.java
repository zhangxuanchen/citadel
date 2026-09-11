package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull Boolean enabled,
        Set<Long> roleIds
) {
}
