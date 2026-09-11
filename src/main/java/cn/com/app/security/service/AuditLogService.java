package cn.com.app.security.service;

import cn.com.app.security.api.dto.AuditLogResponse;
import cn.com.app.security.domain.AuditLog;
import cn.com.app.security.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String module, String operation, String targetType, Object targetId, String detail) {
        AuditLog auditLog = new AuditLog(
                currentUser(),
                module,
                operation,
                targetType,
                targetId == null ? null : String.valueOf(targetId),
                truncate(detail),
                clientIp()
        );
        auditLogRepository.insertAuditLog(auditLog);
        log.info(
                "audit_log operator={} module={} operation={} targetType={} targetId={} detail={}",
                auditLog.getOperator(),
                module,
                operation,
                targetType,
                targetId,
                auditLog.getDetail()
        );
    }

    public List<AuditLogResponse> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditLogRepository.findRecent(safeLimit).stream()
                .map(auditLog -> new AuditLogResponse(
                        auditLog.getId(),
                        auditLog.getOperator(),
                        auditLog.getModule(),
                        auditLog.getOperation(),
                        auditLog.getTargetType(),
                        auditLog.getTargetId(),
                        auditLog.getDetail(),
                        auditLog.getClientIp(),
                        auditLog.getOccurredAt()
                ))
                .toList();
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "N/A";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
