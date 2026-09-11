package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignPermissionsRequest(
        @NotNull Set<Long> permissionIds
) {
}
