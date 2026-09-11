package cn.com.app.security.api;

import cn.com.app.security.api.dto.AssignPermissionsRequest;
import cn.com.app.security.api.dto.RoleRequest;
import cn.com.app.security.api.dto.RoleResponse;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.RoleManagementService;
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
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class RoleController {

    private final RoleManagementService roleManagementService;

    public RoleController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @GetMapping
    @OperationLog(module = "role", operation = "list_roles")
    public Object listRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (isPagedRequest(keyword, page, size)) {
            return roleManagementService.searchRoles(keyword, page == null ? 0 : page, size == null ? 10 : size);
        }
        return roleManagementService.listRoles();
    }

    @GetMapping("/{id}")
    @OperationLog(module = "role", operation = "get_role")
    public RoleResponse getRole(@PathVariable Long id) {
        return roleManagementService.getRole(id);
    }

    @PostMapping
    @OperationLog(module = "role", operation = "create_role")
    public RoleResponse createRole(@Valid @RequestBody RoleRequest request) {
        return roleManagementService.createRole(request);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "role", operation = "update_role")
    public RoleResponse updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleManagementService.updateRole(id, request);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "role", operation = "delete_role")
    public Map<String, String> deleteRole(@PathVariable Long id) {
        roleManagementService.deleteRole(id);
        return Map.of("message", "role deleted");
    }

    @PutMapping("/{id}/permissions")
    @OperationLog(module = "role", operation = "assign_permissions")
    public RoleResponse assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        return roleManagementService.assignPermissions(id, request.permissionIds());
    }

    private boolean isPagedRequest(String keyword, Integer page, Integer size) {
        return keyword != null || page != null || size != null;
    }
}
