package cn.com.app.security.domain;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import tk.mybatis.mapper.annotation.KeySql;

@Table(name = "sys_refresh_token")
public class RefreshToken {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "token")
    private String token;

    @Column(name = "user_id")
    private Long userId;

    @Transient
    private UserAccount user;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked")
    private Boolean revoked = false;

    public RefreshToken() {
    }

    public RefreshToken(String token, UserAccount user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.userId = user.getId();
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return !Boolean.TRUE.equals(revoked) && expiresAt.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void revoke() {
        this.revoked = true;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return Boolean.TRUE.equals(revoked);
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
}
