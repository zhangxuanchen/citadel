package cn.com.app.security.domain;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import tk.mybatis.mapper.annotation.KeySql;

@Table(name = "sys_permission")
public class Permission {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "client_app_id")
    private Long clientAppId;

    @Transient
    private ClientApp clientApp;

    @Transient
    private String appCode;

    @Transient
    private String appName;

    public Permission() {
    }

    public Permission(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Permission(String code, String name, ClientApp clientApp) {
        this.code = code;
        this.name = name;
        setClientApp(clientApp);
    }

    public void update(String code, String name, ClientApp clientApp) {
        this.code = code;
        this.name = name;
        setClientApp(clientApp);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getClientAppId() {
        return clientAppId;
    }

    public void setClientAppId(Long clientAppId) {
        this.clientAppId = clientAppId;
    }

    public ClientApp getClientApp() {
        return clientApp;
    }

    public void setClientApp(ClientApp clientApp) {
        this.clientApp = clientApp;
        this.clientAppId = clientApp == null ? null : clientApp.getId();
        this.appCode = clientApp == null ? null : clientApp.getCode();
        this.appName = clientApp == null ? null : clientApp.getName();
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
