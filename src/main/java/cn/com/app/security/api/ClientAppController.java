package cn.com.app.security.api;

import cn.com.app.security.api.dto.ClientAppRequest;
import cn.com.app.security.api.dto.ClientAppResponse;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.ClientAppManagementService;
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
@RequestMapping("/api/client-apps")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class ClientAppController {

    private final ClientAppManagementService clientAppManagementService;

    public ClientAppController(ClientAppManagementService clientAppManagementService) {
        this.clientAppManagementService = clientAppManagementService;
    }

    @GetMapping
    @OperationLog(module = "client_app", operation = "list_client_apps")
    public Object listClientApps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (isPagedRequest(keyword, page, size)) {
            return clientAppManagementService.searchClientApps(keyword, page == null ? 0 : page, size == null ? 10 : size);
        }
        return clientAppManagementService.listClientApps();
    }

    @GetMapping("/{id}")
    @OperationLog(module = "client_app", operation = "get_client_app")
    public ClientAppResponse getClientApp(@PathVariable Long id) {
        return clientAppManagementService.getClientApp(id);
    }

    @PostMapping
    @OperationLog(module = "client_app", operation = "create_client_app")
    public ClientAppResponse createClientApp(@Valid @RequestBody ClientAppRequest request) {
        return clientAppManagementService.createClientApp(request);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "client_app", operation = "update_client_app")
    public ClientAppResponse updateClientApp(@PathVariable Long id, @Valid @RequestBody ClientAppRequest request) {
        return clientAppManagementService.updateClientApp(id, request);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "client_app", operation = "delete_client_app")
    public Map<String, String> deleteClientApp(@PathVariable Long id) {
        clientAppManagementService.deleteClientApp(id);
        return Map.of("message", "client app deleted");
    }

    private boolean isPagedRequest(String keyword, Integer page, Integer size) {
        return keyword != null || page != null || size != null;
    }
}
