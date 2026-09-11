package cn.com.app.security.service;

import cn.com.app.security.api.dto.LoginResponse;
import cn.com.app.security.api.dto.SsoTicketRequest;
import cn.com.app.security.api.dto.SsoTicketResponse;
import cn.com.app.security.domain.ClientApp;
import cn.com.app.security.domain.SsoTicket;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.repository.ClientAppRepository;
import cn.com.app.security.repository.SsoTicketRepository;
import cn.com.app.security.repository.UserAccountRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);

    private final ClientAppRepository clientAppRepository;
    private final UserAccountRepository userAccountRepository;
    private final SsoTicketRepository ssoTicketRepository;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public SsoService(
            ClientAppRepository clientAppRepository,
            UserAccountRepository userAccountRepository,
            SsoTicketRepository ssoTicketRepository,
            AuthService authService,
            AuditLogService auditLogService
    ) {
        this.clientAppRepository = clientAppRepository;
        this.userAccountRepository = userAccountRepository;
        this.ssoTicketRepository = ssoTicketRepository;
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SsoTicketResponse createTicket(UserAccount currentUser, SsoTicketRequest request) {
        ClientApp clientApp = clientAppRepository.findByCode(request.appCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client app not found"));
        if (!Boolean.TRUE.equals(clientApp.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client app is disabled");
        }
        validateRedirectUri(request.redirectUri());

        UserAccount user = userAccountRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        SsoTicket ssoTicket = new SsoTicket(
                "SSO-" + UUID.randomUUID(),
                user,
                clientApp,
                request.redirectUri(),
                request.state(),
                expiresAt
        );
        ssoTicketRepository.save(ssoTicket);
        log.info("sso_ticket_created userId={} username={} appCode={} expiresAt={}",
                user.getId(), user.getUsername(), clientApp.getCode(), expiresAt);
        auditLogService.record(
                "sso",
                "create_ticket",
                "client_app",
                clientApp.getId(),
                "appCode=%s,username=%s,redirectUri=%s".formatted(clientApp.getCode(), user.getUsername(), request.redirectUri())
        );
        return new SsoTicketResponse(
                ssoTicket.getTicket(),
                clientApp.getCode(),
                request.redirectUri(),
                request.state(),
                expiresAt,
                buildRedirectUrl(request.redirectUri(), ssoTicket.getTicket(), request.state())
        );
    }

    @Transactional
    public LoginResponse exchangeToken(String ticketValue) {
        SsoTicket ssoTicket = ssoTicketRepository.findByTicket(ticketValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SSO ticket"));
        if (!ssoTicket.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO ticket expired or used");
        }

        UserAccount user = userAccountRepository.findById(ssoTicket.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO ticket user missing"));
        ClientApp clientApp = clientAppRepository.findById(ssoTicket.getClientAppId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO ticket app missing"));
        if (!Boolean.TRUE.equals(clientApp.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client app is disabled");
        }

        ssoTicket.markUsed();
        ssoTicketRepository.save(ssoTicket);
        log.info("sso_ticket_exchanged userId={} username={} appCode={}", user.getId(), user.getUsername(), clientApp.getCode());
        auditLogService.record(
                "sso",
                "exchange_ticket",
                "client_app",
                clientApp.getId(),
                "appCode=%s,username=%s".formatted(clientApp.getCode(), user.getUsername())
        );
        return authService.issueTokens(user);
    }

    private void validateRedirectUri(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid redirectUri");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri must start with http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirectUri host is required");
        }
    }

    private String buildRedirectUrl(String redirectUri, String ticket, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("ticket", ticket);
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        return builder.build(true).toUriString();
    }
}
