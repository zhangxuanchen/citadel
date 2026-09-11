package cn.com.app.security.api;

import cn.com.app.security.api.dto.LoginResponse;
import cn.com.app.security.api.dto.SsoTicketRequest;
import cn.com.app.security.api.dto.SsoTicketResponse;
import cn.com.app.security.api.dto.SsoTokenRequest;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.service.SsoService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private final SsoService ssoService;

    public SsoController(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @PostMapping("/tickets")
    @OperationLog(module = "sso", operation = "create_ticket")
    public SsoTicketResponse createTicket(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody SsoTicketRequest request
    ) {
        return ssoService.createTicket(currentUser, request);
    }

    @PostMapping("/token")
    @OperationLog(module = "sso", operation = "exchange_ticket")
    public LoginResponse exchangeToken(@Valid @RequestBody SsoTokenRequest request) {
        return ssoService.exchangeToken(request.ticket());
    }
}
