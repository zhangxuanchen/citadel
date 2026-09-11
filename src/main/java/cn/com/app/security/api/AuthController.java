package cn.com.app.security.api;

import cn.com.app.security.api.dto.ChangePasswordRequest;
import cn.com.app.security.api.dto.AuthzDiscoveryResponse;
import cn.com.app.security.api.dto.AuthzUserInfoResponse;
import cn.com.app.security.api.dto.CaptchaResponse;
import cn.com.app.security.api.dto.LoginRequest;
import cn.com.app.security.api.dto.LoginResponse;
import cn.com.app.security.api.dto.LogoutRequest;
import cn.com.app.security.api.dto.RefreshTokenRequest;
import cn.com.app.security.api.dto.RegisterRequest;
import cn.com.app.security.api.dto.ResetPasswordRequest;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.config.JwtProperties;
import cn.com.app.security.logging.OperationLog;
import cn.com.app.security.security.JwtService;
import cn.com.app.security.security.RsaKeyUtils;
import cn.com.app.security.service.AuthService;
import cn.com.app.security.service.CaptchaService;
import com.google.code.kaptcha.Producer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final Producer captchaProducer;
    private final CaptchaService captchaService;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final boolean returnCaptchaCode;

    public AuthController(
            AuthService authService,
            Producer captchaProducer,
            CaptchaService captchaService,
            JwtProperties jwtProperties,
              JwtService jwtService,
            @Value("${authz.captcha.return-code:false}") boolean returnCaptchaCode
    ) {
        this.authService = authService;
        this.captchaProducer = captchaProducer;
        this.captchaService = captchaService;
        this.jwtProperties = jwtProperties;
          this.jwtService = jwtService;
        this.returnCaptchaCode = returnCaptchaCode;
    }

    @GetMapping("/discovery")
    public AuthzDiscoveryResponse discovery() {
        return new AuthzDiscoveryResponse(
                jwtProperties.issuer(),
                "Bearer",
                "Authorization: Bearer <accessToken>",
                  "RS256",
                  "/api/auth/jwks",
                "/api/auth/userinfo",
                List.of("/api/client-apps", "/api/permissions", "/api/roles", "/api/users", "/api/audit-logs")
        );
    }

      @GetMapping("/jwks")
      public Map<String, Object> jwks() {
          return Map.of("keys", List.of(RsaKeyUtils.toJwk(jwtProperties.keyId(), jwtService.publicKey())));
      }

    @GetMapping("/captcha")
    public CaptchaResponse captcha() {
        String text = captchaProducer.createText();
        BufferedImage image = captchaProducer.createImage(text);
        String captchaId = captchaService.store(text);
        return new CaptchaResponse(captchaId, toDataUri(image), returnCaptchaCode ? text : null);
    }

    @PostMapping("/register")
    @OperationLog(module = "auth", operation = "register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @OperationLog(module = "auth", operation = "login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @OperationLog(module = "auth", operation = "refresh_token")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @OperationLog(module = "auth", operation = "logout")
    public Map<String, String> logout(HttpServletRequest servletRequest, @RequestBody(required = false) LogoutRequest request) {
        authService.logout(resolveToken(servletRequest), request);
        return Map.of("message", "logout success");
    }

    @GetMapping("/userinfo")
    @OperationLog(module = "auth", operation = "userinfo")
    public AuthzUserInfoResponse userinfo(@AuthenticationPrincipal UserAccount currentUser) {
        List<String> roles = currentUser.getRoles().stream()
                .map(role -> role.getCode())
                .toList();
        List<String> authorities = currentUser.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .distinct()
                .toList();
        return new AuthzUserInfoResponse(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getDisplayName(),
                jwtProperties.issuer(),
                roles,
                authorities
        );
    }

    @PostMapping("/password/change")
    @OperationLog(module = "auth", operation = "change_password")
    public Map<String, String> changePassword(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUser, request);
        return Map.of("message", "password changed");
    }

    @PostMapping("/password/reset/{userId}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @OperationLog(module = "auth", operation = "reset_password")
    public Map<String, String> resetPassword(@PathVariable Long userId, @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(userId, request);
        return Map.of("message", "password reset");
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private String toDataUri(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Captcha image generate failed", ex);
        }
    }
}
