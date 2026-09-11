package cn.com.app.security.api.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String operator,
        String module,
        String operation,
        String targetType,
        String targetId,
        String detail,
        String clientIp,
        Instant occurredAt
) {
}
