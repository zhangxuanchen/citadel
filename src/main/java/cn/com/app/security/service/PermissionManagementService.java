package cn.com.app.security.service;

import cn.com.app.security.api.dto.PageResponse;
import cn.com.app.security.api.dto.PermissionRequest;
import cn.com.app.security.api.dto.PermissionResponse;
import cn.com.app.security.domain.ClientApp;
import cn.com.app.security.domain.Permission;
import cn.com.app.security.repository.PermissionRepository;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionManagementService {

    private static final Logger log = LoggerFactory.getLogger(PermissionManagementService.class);

    private final PermissionRepository permissionRepository;
    private final ClientAppManagementService clientAppManagementService;
    private final AuditLogService auditLogService;

    public PermissionManagementService(
            PermissionRepository permissionRepository,
            ClientAppManagementService clientAppManagementService,
            AuditLogService auditLogService
    ) {
        this.permissionRepository = permissionRepository;
        this.clientAppManagementService = clientAppManagementService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> searchPermissions(String keyword, int page, int size) {
        List<PermissionResponse> filtered = listPermissions().stream()
                .filter(permission -> matches(keyword, permission.code(), permission.name(), permission.appCode(), permission.appName()))
                .toList();
        return PageResponse.of(filtered, page, size);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermission(Long id) {
        return toResponse(findPermission(id));
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            log.warn("permission_create_failed reason=code_exists code={}", request.code());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission code already exists");
        }
        ClientApp clientApp = clientAppManagementService.findByCodeOrDefault(request.appCode());
        Permission permission = new Permission(request.code(), request.name(), clientApp);
        permissionRepository.save(permission);
        log.info("permission_create_success permissionId={} code={}", permission.getId(), permission.getCode());
        auditLogService.record(
                "permission",
                "create_permission",
                "permission",
                permission.getId(),
                "appCode=%s,code=%s,name=%s".formatted(clientApp.getCode(), permission.getCode(), permission.getName())
        );
        return toResponse(permission);
    }

    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        Permission permission = findPermission(id);
        String before = "code=%s,name=%s".formatted(permission.getCode(), permission.getName());
        permissionRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    log.warn("permission_update_failed reason=code_exists permissionId={} code={}", id, request.code());
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Permission code already exists");
                });
        ClientApp clientApp = clientAppManagementService.findByCodeOrDefault(request.appCode());
        permission.update(request.code(), request.name(), clientApp);
        permissionRepository.save(permission);
        log.info("permission_update_success permissionId={} code={}", permission.getId(), permission.getCode());
        auditLogService.record(
                "permission",
                "update_permission",
                "permission",
                permission.getId(),
                "before={%s},after={appCode=%s,code=%s,name=%s}".formatted(
                        before,
                        clientApp.getCode(),
                        permission.getCode(),
                        permission.getName()
                )
        );
        return toResponse(permission);
    }

    @Transactional
    public void deletePermission(Long id) {
        Permission permission = findPermission(id);
        String detail = "appCode=%s,code=%s,name=%s".formatted(
                permission.getClientApp() == null ? null : permission.getClientApp().getCode(),
                permission.getCode(),
                permission.getName()
        );
        permissionRepository.deleteRolePermissionsByPermissionId(permission.getId());
        permissionRepository.delete(permission);
        log.info("permission_delete_success permissionId={} code={}", permission.getId(), permission.getCode());
        auditLogService.record("permission", "delete_permission", "permission", permission.getId(), detail);
    }

    private Permission findPermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
    }

    private PermissionResponse toResponse(Permission permission) {
        ClientApp clientApp = permission.getClientApp();
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                clientApp == null ? null : clientApp.getCode(),
                clientApp == null ? null : clientApp.getName()
        );
    }

    private boolean matches(String keyword, String... values) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(normalized)) {
                return true;
            }
        }
        return false;
    }
}
