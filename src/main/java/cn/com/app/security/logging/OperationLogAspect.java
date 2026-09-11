package cn.com.app.security.logging;

import cn.com.app.security.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    public static final String AUDIT_RECORDED_ATTRIBUTE = OperationLogAspect.class.getName() + ".AUDIT_RECORDED";

    private final AuditLogService auditLogService;

    public OperationLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long durationMs = System.currentTimeMillis() - start;
            log.info(
                    "operation_log module={} operation={} status=success durationMs={} user={} method={} uri={} args={}",
                    operationLog.module(),
                    operationLog.operation(),
                    durationMs,
                    currentUser(),
                    requestMethod(),
                    requestUri(),
                    safeArgs(joinPoint)
            );
            auditLogService.record(
                    operationLog.module(),
                    operationLog.operation(),
                    "api",
                    requestUri(),
                    "status=success,method=%s,uri=%s,durationMs=%s,args=%s".formatted(
                            requestMethod(),
                            requestUri(),
                            durationMs,
                            safeArgs(joinPoint)
                    )
            );
            markAuditRecorded();
            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn(
                    "operation_log module={} operation={} status=failure durationMs={} user={} method={} uri={} error={}",
                    operationLog.module(),
                    operationLog.operation(),
                    durationMs,
                    currentUser(),
                    requestMethod(),
                    requestUri(),
                    ex.getClass().getSimpleName()
            );
            auditLogService.record(
                    operationLog.module(),
                    operationLog.operation(),
                    "api",
                    requestUri(),
                    "status=failure,method=%s,uri=%s,durationMs=%s,error=%s,args=%s".formatted(
                            requestMethod(),
                            requestUri(),
                            durationMs,
                            ex.getClass().getSimpleName(),
                            safeArgs(joinPoint)
                    )
            );
            markAuditRecorded();
            throw ex;
        }
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String requestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? "N/A" : request.getMethod();
    }

    private String requestUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? "N/A" : request.getRequestURI();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private void markAuditRecorded() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(AUDIT_RECORDED_ATTRIBUTE, Boolean.TRUE);
        }
    }

    private String safeArgs(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
                .filter(arg -> arg instanceof Number || arg instanceof String || arg instanceof Boolean)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
