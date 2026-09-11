package cn.com.app.security.repository;

import cn.com.app.security.domain.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;

public interface AuditLogRepository extends Mapper<AuditLog> {

    default void insertAuditLog(AuditLog auditLog) {
        insertSelective(auditLog);
    }

    @Select("""
            select id, operator, module, operation, target_type, target_id, detail, client_ip, occurred_at
            from sys_audit_log
            order by occurred_at desc, id desc
            limit #{limit}
            """)
    List<AuditLog> findRecent(@Param("limit") int limit);
}
