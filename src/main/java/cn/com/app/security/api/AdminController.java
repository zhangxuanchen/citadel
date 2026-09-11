package cn.com.app.security.api;

import cn.com.app.security.logging.OperationLog;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "admin", operation = "dashboard")
    public Map<String, String> dashboard() {
        return Map.of("message", "admin dashboard");
    }
}
