package cn.com.app.security.domain;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import tk.mybatis.mapper.annotation.KeySql;

@Table(name = "sys_blacklisted_token")
public class BlacklistedToken {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public BlacklistedToken() {
    }

    public BlacklistedToken(String tokenId, Instant expiresAt) {
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
