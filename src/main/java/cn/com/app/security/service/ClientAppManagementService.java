package cn.com.app.security.service;

import cn.com.app.security.api.dto.ClientAppRequest;
import cn.com.app.security.api.dto.ClientAppResponse;
import cn.com.app.security.api.dto.PageResponse;
import cn.com.app.security.domain.ClientApp;
import cn.com.app.security.repository.ClientAppRepository;
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
public class ClientAppManagementService {

    public static final String DEFAULT_APP_CODE = "AUTHZ";

    private static final Logger log = LoggerFactory.getLogger(ClientAppManagementService.class);

    private final ClientAppRepository clientAppRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    public ClientAppManagementService(
            ClientAppRepository clientAppRepository,
            PermissionRepository permissionRepository,
            AuditLogService auditLogService
    ) {
        this.clientAppRepository = clientAppRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ClientAppResponse> listClientApps() {
        return clientAppRepository.findAll().stream()
                .sorted(Comparator.comparing(ClientApp::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientAppResponse> searchClientApps(String keyword, int page, int size) {
        List<ClientAppResponse> filtered = listClientApps().stream()
                .filter(app -> matches(keyword, app.code(), app.name(), app.description(), app.enabled() ? "启用" : "停用"))
                .toList();
        return PageResponse.of(filtered, page, size);
    }

    @Transactional(readOnly = true)
    public ClientAppResponse getClientApp(Long id) {
        return toResponse(findClientApp(id));
    }

    @Transactional
    public ClientAppResponse createClientApp(ClientAppRequest request) {
        if (clientAppRepository.existsByCode(request.code())) {
            log.warn("client_app_create_failed reason=code_exists appCode={}", request.code());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client app code already exists");
        }
        ClientApp clientApp = new ClientApp(request.code(), request.name(), request.description());
        clientApp.setEnabled(request.enabled());
        clientAppRepository.save(clientApp);
        log.info("client_app_create_success appId={} appCode={}", clientApp.getId(), clientApp.getCode());
        auditLogService.record(
                "client_app",
                "create_client_app",
                "client_app",
                clientApp.getId(),
                "appCode=%s,name=%s,enabled=%s".formatted(clientApp.getCode(), clientApp.getName(), Boolean.TRUE.equals(clientApp.getEnabled()))
        );
        return toResponse(clientApp);
    }

    @Transactional
    public ClientAppResponse updateClientApp(Long id, ClientAppRequest request) {
        ClientApp clientApp = findClientApp(id);
        clientAppRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    log.warn("client_app_update_failed reason=code_exists appId={} appCode={}", id, request.code());
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Client app code already exists");
                });
        String before = "appCode=%s,name=%s,enabled=%s".formatted(
                clientApp.getCode(),
                clientApp.getName(),
                Boolean.TRUE.equals(clientApp.getEnabled())
        );
        clientApp.update(request.code(), request.name(), request.description(), request.enabled());
        clientAppRepository.save(clientApp);
        log.info("client_app_update_success appId={} appCode={}", clientApp.getId(), clientApp.getCode());
        auditLogService.record(
                "client_app",
                "update_client_app",
                "client_app",
                clientApp.getId(),
                "before={%s},after={appCode=%s,name=%s,enabled=%s}".formatted(
                        before,
                        clientApp.getCode(),
                        clientApp.getName(),
                        Boolean.TRUE.equals(clientApp.getEnabled())
                )
        );
        return toResponse(clientApp);
    }

    @Transactional
    public void deleteClientApp(Long id) {
        ClientApp clientApp = findClientApp(id);
        if (permissionRepository.existsByClientAppId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client app still has permissions");
        }
        clientAppRepository.delete(clientApp);
        log.info("client_app_delete_success appId={} appCode={}", clientApp.getId(), clientApp.getCode());
        auditLogService.record(
                "client_app",
                "delete_client_app",
                "client_app",
                clientApp.getId(),
                "appCode=%s,name=%s".formatted(clientApp.getCode(), clientApp.getName())
        );
    }

    public ClientApp findByCodeOrDefault(String appCode) {
        String normalized = appCode == null || appCode.isBlank() ? DEFAULT_APP_CODE : appCode.trim();
        return clientAppRepository.findByCode(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client app not found"));
    }

    private ClientApp findClientApp(Long id) {
        return clientAppRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client app not found"));
    }

    private ClientAppResponse toResponse(ClientApp clientApp) {
        return new ClientAppResponse(
                clientApp.getId(),
                clientApp.getCode(),
                clientApp.getName(),
                clientApp.getDescription(),
                clientApp.getEnabled()
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
