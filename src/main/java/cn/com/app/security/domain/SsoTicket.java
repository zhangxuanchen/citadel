package cn.com.app.security.domain;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import tk.mybatis.mapper.annotation.KeySql;

@Table(name = "sys_sso_ticket")
public class SsoTicket {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "ticket")
    private String ticket;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "client_app_id")
    private Long clientAppId;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @Column(name = "state")
    private String state;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used")
    private Boolean used = false;

    @Transient
    private UserAccount user;

    @Transient
    private ClientApp clientApp;

    public SsoTicket() {
    }

    public SsoTicket(String ticket, UserAccount user, ClientApp clientApp, String redirectUri, String state, Instant expiresAt) {
        this.ticket = ticket;
        setUser(user);
        setClientApp(clientApp);
        this.redirectUri = redirectUri;
        this.state = state;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    public boolean isActive() {
        return !Boolean.TRUE.equals(used) && expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public void markUsed() {
        this.used = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getClientAppId() {
        return clientAppId;
    }

    public void setClientAppId(Long clientAppId) {
        this.clientAppId = clientAppId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }

    public ClientApp getClientApp() {
        return clientApp;
    }

    public void setClientApp(ClientApp clientApp) {
        this.clientApp = clientApp;
        this.clientAppId = clientApp == null ? null : clientApp.getId();
    }
}
