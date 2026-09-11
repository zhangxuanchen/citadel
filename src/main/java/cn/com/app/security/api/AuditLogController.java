package cn.com.app.security.api;

import cn.com.app.security.api.dto.AuditLogResponse;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.AuditLogService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @OperationLog(module = "audit", operation = "list_audit_logs")
    public List<AuditLogResponse> listAuditLogs(@RequestParam(defaultValue = "100") int limit) {
        return auditLogService.listRecent(limit);
    }
}
