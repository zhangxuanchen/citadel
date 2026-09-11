package cn.com.app.security.api;

import cn.com.app.security.api.dto.AssignRolesRequest;
import cn.com.app.security.api.dto.CreateUserRequest;
import cn.com.app.security.api.dto.UpdateUserRequest;
import cn.com.app.security.api.dto.UserProfileResponse;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    @OperationLog(module = "user", operation = "current_user")
    public UserProfileResponse currentUser(@AuthenticationPrincipal UserAccount userAccount) {
        List<String> roles = userAccount.getRoles().stream()
                .map(role -> role.getCode())
                .toList();
        List<String> permissions = userAccount.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .distinct()
                .toList();
        return new UserProfileResponse(
                userAccount.getId(),
                userAccount.getUsername(),
                userAccount.getDisplayName(),
                  userAccount.isEnabled(),
                roles,
                permissions
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "list_users")
    public Object listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (isPagedRequest(keyword, page, size)) {
            return userManagementService.searchUsers(keyword, page == null ? 0 : page, size == null ? 10 : size);
        }
        return userManagementService.listUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "get_user")
    public UserProfileResponse getUser(@PathVariable Long id) {
        return userManagementService.getUser(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "create_user")
    public UserProfileResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "update_user")
    public UserProfileResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userManagementService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "delete_user")
    public Map<String, String> deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(id);
        return Map.of("message", "user deleted");
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "user", operation = "assign_roles")
    public UserProfileResponse assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return userManagementService.assignRoles(id, request.roleIds());
    }

    private boolean isPagedRequest(String keyword, Integer page, Integer size) {
        return keyword != null || page != null || size != null;
    }
}
