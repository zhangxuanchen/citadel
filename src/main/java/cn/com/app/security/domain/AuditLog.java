package cn.com.app.security.domain;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import tk.mybatis.mapper.annotation.KeySql;

@Table(name = "sys_audit_log")
public class AuditLog {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "operator")
    private String operator;

    @Column(name = "module")
    private String module;

    @Column(name = "operation")
    private String operation;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "detail")
    private String detail;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    public AuditLog() {
    }

    public AuditLog(String operator, String module, String operation, String targetType, String targetId, String detail, String clientIp) {
        this.operator = operator;
        this.module = module;
        this.operation = operation;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.clientIp = clientIp;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOperator() {
        return operator;
    }

    public String getModule() {
        return module;
    }

    public String getOperation() {
        return operation;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
