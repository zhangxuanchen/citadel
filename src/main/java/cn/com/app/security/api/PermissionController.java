package cn.com.app.security.api;

import cn.com.app.security.api.dto.PermissionRequest;
import cn.com.app.security.api.dto.PermissionResponse;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.PermissionManagementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class PermissionController {

    private final PermissionManagementService permissionManagementService;

    public PermissionController(PermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @GetMapping
    @OperationLog(module = "permission", operation = "list_permissions")
    public Object listPermissions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (isPagedRequest(keyword, page, size)) {
            return permissionManagementService.searchPermissions(keyword, page == null ? 0 : page, size == null ? 10 : size);
        }
        return permissionManagementService.listPermissions();
    }

    @GetMapping("/{id}")
    @OperationLog(module = "permission", operation = "get_permission")
    public PermissionResponse getPermission(@PathVariable Long id) {
        return permissionManagementService.getPermission(id);
    }

    @PostMapping
    @OperationLog(module = "permission", operation = "create_permission")
    public PermissionResponse createPermission(@Valid @RequestBody PermissionRequest request) {
        return permissionManagementService.createPermission(request);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "permission", operation = "update_permission")
    public PermissionResponse updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return permissionManagementService.updatePermission(id, request);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "permission", operation = "delete_permission")
    public Map<String, String> deletePermission(@PathVariable Long id) {
        permissionManagementService.deletePermission(id);
        return Map.of("message", "permission deleted");
    }

    private boolean isPagedRequest(String keyword, Integer page, Integer size) {
        return keyword != null || page != null || size != null;
    }
}
