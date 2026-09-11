package cn.com.app.security.service;

import cn.com.app.security.api.dto.PageResponse;
import cn.com.app.security.api.dto.PermissionResponse;
import cn.com.app.security.api.dto.RoleRequest;
import cn.com.app.security.api.dto.RoleResponse;
import cn.com.app.security.domain.Permission;
import cn.com.app.security.domain.Role;
import cn.com.app.security.repository.PermissionRepository;
import cn.com.app.security.repository.RoleRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoleManagementService {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementService.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    public RoleManagementService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AuditLogService auditLogService
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> searchRoles(String keyword, int page, int size) {
        List<RoleResponse> filtered = listRoles().stream()
                .filter(role -> roleMatches(keyword, role))
                .toList();
        return PageResponse.of(filtered, page, size);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(Long id) {
        return toResponse(findRole(id));
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            log.warn("role_create_failed reason=code_exists code={}", request.code());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        }
        Role role = new Role(request.code(), request.name());
        roleRepository.save(role);
        log.info("role_create_success roleId={} code={}", role.getId(), role.getCode());
        auditLogService.record(
                "role",
                "create_role",
                "role",
                role.getId(),
                "code=%s,name=%s".formatted(role.getCode(), role.getName())
        );
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = findRole(id);
        String before = "code=%s,name=%s".formatted(role.getCode(), role.getName());
        roleRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    log.warn("role_update_failed reason=code_exists roleId={} code={}", id, request.code());
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
                });
        role.update(request.code(), request.name());
        roleRepository.save(role);
        log.info("role_update_success roleId={} code={}", role.getId(), role.getCode());
        auditLogService.record(
                "role",
                "update_role",
                "role",
                role.getId(),
                "before={%s},after={code=%s,name=%s}".formatted(before, role.getCode(), role.getName())
        );
        return toResponse(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = findRole(id);
        String detail = "code=%s,name=%s,permissions=%s".formatted(role.getCode(), role.getName(), permissionCodes(role));
        roleRepository.deleteUserRolesByRoleId(role.getId());
        roleRepository.deletePermissionsByRoleId(role.getId());
        roleRepository.delete(role);
        log.info("role_delete_success roleId={} code={}", role.getId(), role.getCode());
        auditLogService.record("role", "delete_role", "role", role.getId(), detail);
    }

    @Transactional
    public RoleResponse assignPermissions(Long id, Set<Long> permissionIds) {
        Role role = findRole(id);
        List<String> beforePermissions = permissionCodes(role);
        role.replacePermissions(findPermissions(permissionIds));
        roleRepository.replacePermissions(role.getId(), permissionIds);
        role = findRole(id);
        log.info("role_assign_permissions_success roleId={} code={} permissionIds={}", role.getId(), role.getCode(), permissionIds);
        auditLogService.record(
                "role",
                "assign_permissions",
                "role",
                role.getId(),
                "roleCode=%s,beforePermissions=%s,afterPermissions=%s".formatted(
                        role.getCode(),
                        beforePermissions,
                        permissionCodes(role)
                )
        );
        return toResponse(role);
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private Set<Permission> findPermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Set.of();
        }
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some permissions do not exist");
        }
        return new LinkedHashSet<>(permissions);
    }

    private RoleResponse toResponse(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(),
                        permission.getCode(),
                        permission.getName(),
                        permission.getClientApp() == null ? permission.getAppCode() : permission.getClientApp().getCode(),
                        permission.getClientApp() == null ? permission.getAppName() : permission.getClientApp().getName()
                ))
                .toList();
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), permissions);
    }

    private List<String> permissionCodes(Role role) {
        return role.getPermissions().stream()
                .map(Permission::getCode)
                .toList();
    }

    private boolean roleMatches(String keyword, RoleResponse role) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        if (contains(role.code(), normalized) || contains(role.name(), normalized)) {
            return true;
        }
        return role.permissions().stream().anyMatch(permission ->
                contains(permission.code(), normalized)
                        || contains(permission.name(), normalized)
                        || contains(permission.appCode(), normalized)
                        || contains(permission.appName(), normalized)
        );
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }
}
