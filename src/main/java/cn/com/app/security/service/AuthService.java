package cn.com.app.security.service;

import cn.com.app.security.api.dto.ChangePasswordRequest;
import cn.com.app.security.api.dto.LoginRequest;
import cn.com.app.security.api.dto.LoginResponse;
import cn.com.app.security.api.dto.LogoutRequest;
import cn.com.app.security.api.dto.RefreshTokenRequest;
import cn.com.app.security.api.dto.RegisterRequest;
import cn.com.app.security.api.dto.ResetPasswordRequest;
import cn.com.app.security.config.JwtProperties;
import cn.com.app.security.domain.BlacklistedToken;
import cn.com.app.security.domain.RefreshToken;
import cn.com.app.security.domain.Role;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.repository.BlacklistedTokenRepository;
import cn.com.app.security.repository.RefreshTokenRepository;
import cn.com.app.security.repository.RoleRepository;
import cn.com.app.security.repository.UserAccountRepository;
import cn.com.app.security.security.JwtService;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            BlacklistedTokenRepository blacklistedTokenRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            CaptchaService captchaService,
            LoginAttemptService loginAttemptService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            log.warn("auth_register_failed reason=username_exists username={}", request.username());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role userRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default USER role missing"));
        UserAccount user = new UserAccount(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName()
        ).addRole(userRole);
        userAccountRepository.save(user);
        log.info("auth_register_success userId={} username={}", user.getId(), user.getUsername());
        auditLogService.record(
                "auth",
                "register",
                "user",
                user.getId(),
                "username=%s,displayName=%s,role=USER".formatted(user.getUsername(), user.getDisplayName())
        );
        return issueTokens(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        loginAttemptService.assertNotLocked(request.username());
        try {
            captchaService.validate(request.captchaId(), request.captchaCode());
            UserAccount user = (UserAccount) authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            ).getPrincipal();
            loginAttemptService.recordSuccess(request.username());
            log.info("auth_login_success userId={} username={}", user.getId(), user.getUsername());
            return issueTokens(user);
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(request.username(), "bad_credentials");
            throw ex;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                loginAttemptService.recordFailure(request.username(), ex.getReason());
            }
            throw ex;
        }
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        refreshToken.setUser(userAccountRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token user missing")));
        if (!refreshToken.isActive()) {
            log.warn("auth_refresh_failed reason=expired_or_revoked");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        log.info("auth_refresh_success username={}", refreshToken.getUser().getUsername());
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(String accessToken, LogoutRequest request) {
        if (accessToken != null) {
            String tokenId = jwtService.extractTokenId(accessToken);
            if (!blacklistedTokenRepository.existsByTokenId(tokenId)) {
                blacklistedTokenRepository.save(new BlacklistedToken(tokenId, jwtService.extractExpiration(accessToken)));
                log.info("auth_access_token_blacklisted tokenId={}", tokenId);
            }
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenRepository.findByToken(request.refreshToken()).ifPresent(refreshToken -> {
                userAccountRepository.findById(refreshToken.getUserId()).ifPresent(refreshToken::setUser);
                refreshToken.revoke();
                refreshTokenRepository.save(refreshToken);
                log.info("auth_refresh_token_revoked username={}", refreshToken.getUser() == null ? "unknown" : refreshToken.getUser().getUsername());
            });
        }
    }

    @Transactional
    public void changePassword(UserAccount currentUser, ChangePasswordRequest request) {
        UserAccount user = userAccountRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            log.warn("auth_change_password_failed reason=old_password_mismatch userId={} username={}", user.getId(), user.getUsername());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        log.info("auth_change_password_success userId={} username={}", user.getId(), user.getUsername());
        auditLogService.record("auth", "change_password", "user", user.getId(), "username=%s".formatted(user.getUsername()));
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        log.info("auth_reset_password_success userId={} username={}", user.getId(), user.getUsername());
        auditLogService.record("auth", "reset_password", "user", user.getId(), "username=%s".formatted(user.getUsername()));
    }

    public LoginResponse issueTokens(UserAccount user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(new RefreshToken(
                refreshToken,
                user,
                Instant.now().plus(jwtProperties.refreshExpiration())
        ));
        return new LoginResponse(
                "Bearer",
                accessToken,
                refreshToken,
                jwtService.getExpiresInSeconds(),
                jwtProperties.issuer(),
                user.getUsername(),
                authorities(user)
        );
    }

    private Collection<String> authorities(UserAccount user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
