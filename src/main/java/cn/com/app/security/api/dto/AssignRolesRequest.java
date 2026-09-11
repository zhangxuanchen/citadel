package cn.com.app.security.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignRolesRequest(
        @NotNull Set<Long> roleIds
) {
}
