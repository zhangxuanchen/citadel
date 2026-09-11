package cn.com.app.security.api;

import cn.com.app.security.logging.OperationLog;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController 表示这是一个 REST 接口类，方法返回值会自动转成 JSON 响应给前端。
@RestController
// @RequestMapping 定义当前 Controller 的统一接口前缀。
// 例如下面的 /public 方法，完整访问路径就是 /api/demo/permissions/public。
@RequestMapping("/api/demo/permissions")
public class PermissionUsageDemoController {

    // @GetMapping 定义 GET 请求接口，括号里是当前方法的子路径。
    @GetMapping("/public")
    // @OperationLog 是项目里的自定义操作日志注解，会把接口调用写入审计日志。
    // module 表示日志模块，operation 表示具体操作名称。
    @OperationLog(module = "permission_demo", operation = "public_endpoint")
    public Map<String, String> publicEndpoint() {
        return Map.of(
                "permission", "permitAll",
                "description", "Public endpoint. Configure permitAll in SecurityConfig when real public access is needed."
        );
    }

    @GetMapping("/login-required")
    // @PreAuthorize 会在方法执行前做权限判断。
    // isAuthenticated() 表示只要用户已经登录并携带有效 JWT，就允许访问。
    @PreAuthorize("isAuthenticated()")
    @OperationLog(module = "permission_demo", operation = "login_required")
    // Authentication 是 Spring Security 注入的当前登录用户信息。
    // authentication.getName() 可以拿到当前登录用户名。
    public Map<String, String> loginRequired(Authentication authentication) {
        return Map.of(
                "permission", "isAuthenticated()",
                "username", authentication.getName(),
                "description", "Any logged-in user can access this endpoint."
        );
    }

    @GetMapping("/article-read")
    // hasAuthority('ARTICLE_READ') 表示用户必须拥有 ARTICLE_READ 权限。
    // 权限来自数据库中的 sys_permission，并通过角色分配给用户。
    @PreAuthorize("hasAuthority('ARTICLE_READ')")
    @OperationLog(module = "permission_demo", operation = "article_read")
    public Map<String, String> articleRead() {
        return Map.of(
                "permission", "hasAuthority('ARTICLE_READ')",
                "description", "Users with ARTICLE_READ authority can access this endpoint."
        );
    }

    @GetMapping("/article-write")
    // 这个接口演示写权限。普通 user 默认只有 ARTICLE_READ，没有 ARTICLE_WRITE，
    // 所以普通用户访问这里会返回 403 Forbidden。
    @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    @OperationLog(module = "permission_demo", operation = "article_write")
    public Map<String, String> articleWrite() {
        return Map.of(
                "permission", "hasAuthority('ARTICLE_WRITE')",
                "description", "Users with ARTICLE_WRITE authority can access this endpoint."
        );
    }

    @GetMapping("/admin-role")
    // hasRole('ADMIN') 表示需要管理员角色。
    // 注意：Spring Security 会自动把 ADMIN 转成 ROLE_ADMIN 来检查。
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "permission_demo", operation = "admin_role")
    public Map<String, String> adminRole() {
        return Map.of(
                "permission", "hasRole('ADMIN')",
                "description", "Spring Security checks ROLE_ADMIN when hasRole('ADMIN') is used."
        );
    }

    @GetMapping("/user-manage")
    // 项目约定：所有用户、角色、权限等管理后台接口都使用 USER_MANAGE 权限控制。
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "permission_demo", operation = "user_manage")
    public Map<String, String> userManage() {
        return Map.of(
                "permission", "hasAuthority('USER_MANAGE')",
                "description", "Management endpoints in this project should use USER_MANAGE."
        );
    }

    @GetMapping("/read-and-manage")
    // 可以使用 and / or 组合多个权限条件。
    // 这里表示用户必须同时拥有 ARTICLE_READ 和 USER_MANAGE 两个权限。
    @PreAuthorize("hasAuthority('ARTICLE_READ') and hasAuthority('USER_MANAGE')")
    @OperationLog(module = "permission_demo", operation = "read_and_manage")
    public Map<String, String> readAndManage() {
        return Map.of(
                "permission", "hasAuthority('ARTICLE_READ') and hasAuthority('USER_MANAGE')",
                "description", "This endpoint demonstrates combined authority checks."
        );
    }
}
