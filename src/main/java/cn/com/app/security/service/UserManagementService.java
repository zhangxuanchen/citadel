package cn.com.app.security.service;

import cn.com.app.security.api.dto.CreateUserRequest;
import cn.com.app.security.api.dto.PageResponse;
import cn.com.app.security.api.dto.UpdateUserRequest;
import cn.com.app.security.api.dto.UserProfileResponse;
import cn.com.app.security.domain.Role;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.repository.RefreshTokenRepository;
import cn.com.app.security.repository.RoleRepository;
import cn.com.app.security.repository.SsoTicketRepository;
import cn.com.app.security.repository.UserAccountRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SsoTicketRepository ssoTicketRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserManagementService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            SsoTicketRepository ssoTicketRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.ssoTicketRepository = ssoTicketRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers() {
        return userAccountRepository.findAll().stream()
                .sorted(Comparator.comparing(UserAccount::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> searchUsers(String keyword, int page, int size) {
        List<UserProfileResponse> filtered = listUsers().stream()
                .filter(user -> userMatches(keyword, user))
                .toList();
        return PageResponse.of(filtered, page, size);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUser(Long id) {
        return toResponse(findUser(id));
    }

    @Transactional
    public UserProfileResponse createUser(CreateUserRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            log.warn("user_create_failed reason=username_exists username={}", request.username());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        UserAccount user = new UserAccount(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName()
        );
        user.updateProfile(request.displayName(), request.enabled());
        user.replaceRoles(findRoles(request.roleIds()));
        userAccountRepository.save(user);
        log.info("user_create_success userId={} username={} roleIds={}", user.getId(), user.getUsername(), request.roleIds());
        auditLogService.record(
                "user",
                "create_user",
                "user",
                user.getId(),
                "username=%s,displayName=%s,enabled=%s,roles=%s".formatted(
                        user.getUsername(),
                        user.getDisplayName(),
                        user.isEnabled(),
                        roleCodes(user)
                )
        );
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUser(Long id, UpdateUserRequest request) {
        UserAccount user = findUser(id);
        String before = "displayName=%s,enabled=%s".formatted(user.getDisplayName(), user.isEnabled());
        user.updateProfile(request.displayName(), request.enabled());
        userAccountRepository.save(user);
        log.info("user_update_success userId={} username={} enabled={}", user.getId(), user.getUsername(), request.enabled());
        auditLogService.record(
                "user",
                "update_user",
                "user",
                user.getId(),
                "username=%s,before={%s},after={displayName=%s,enabled=%s}".formatted(
                        user.getUsername(),
                        before,
                        user.getDisplayName(),
                        user.isEnabled()
                )
        );
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        UserAccount user = findUser(id);
        String detail = "username=%s,displayName=%s,roles=%s".formatted(user.getUsername(), user.getDisplayName(), roleCodes(user));
        refreshTokenRepository.deleteByUserId(user.getId());
        ssoTicketRepository.deleteByUserId(user.getId());
        userAccountRepository.delete(user);
        log.info("user_delete_success userId={} username={}", user.getId(), user.getUsername());
        auditLogService.record("user", "delete_user", "user", user.getId(), detail);
    }

    @Transactional
    public UserProfileResponse assignRoles(Long id, Set<Long> roleIds) {
        UserAccount user = findUser(id);
        List<String> beforeRoles = roleCodes(user);
        user.replaceRoles(findRoles(roleIds));
        userAccountRepository.save(user);
        log.info("user_assign_roles_success userId={} username={} roleIds={}", user.getId(), user.getUsername(), roleIds);
        auditLogService.record(
                "user",
                "assign_roles",
                "user",
                user.getId(),
                "username=%s,beforeRoles=%s,afterRoles=%s".formatted(user.getUsername(), beforeRoles, roleCodes(user))
        );
        return toResponse(user);
    }

    private UserAccount findUser(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Set<Role> findRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some roles do not exist");
        }
        return new LinkedHashSet<>(roles);
    }

    private UserProfileResponse toResponse(UserAccount user) {
        List<String> roles = roleCodes(user);
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
                .distinct()
                .toList();
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.isEnabled(), roles, permissions);
    }

    private List<String> roleCodes(UserAccount user) {
        return user.getRoles().stream()
                .map(Role::getCode)
                .toList();
    }

    private boolean userMatches(String keyword, UserProfileResponse user) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        if (contains(user.username(), normalized) || contains(user.displayName(), normalized)) {
            return true;
        }
        return user.roles().stream().anyMatch(role -> contains(role, normalized))
                || user.permissions().stream().anyMatch(permission -> contains(permission, normalized))
                || contains(user.enabled() ? "启用" : "停用", normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase().contains(normalizedKeyword);
    }
}
