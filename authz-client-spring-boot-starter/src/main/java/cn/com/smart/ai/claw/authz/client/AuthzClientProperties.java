package cn.com.smart.ai.claw.authz.client;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "authz.client")
public class AuthzClientProperties {

    /**
     * Whether to enable Authz client auto configuration.
     */
    private boolean enabled = true;

    /**
     * Expected JWT issuer. It must be the same as Citadel's issuer.
     */
    private String issuer = "citadel";

    /**
     * RSA public key in PEM format. Configure this or jwksUri.
     */
    private String publicKey;

    /**
     * JWKS endpoint exposed by Citadel. Configure this or publicKey.
     */
    private String jwksUri;

    /**
     * JWT claim name that stores Spring Security authorities.
     */
    private String authoritiesClaim = "authorities";

    /**
     * HTTP Authorization header token prefix.
     */
    private String bearerPrefix = "Bearer ";

    /**
     * Request paths that can be accessed without JWT.
     */
    private List<String> permitPaths = List.of("/actuator/health");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getAuthoritiesClaim() {
        return authoritiesClaim;
    }

    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = authoritiesClaim;
    }

    public String getBearerPrefix() {
        return bearerPrefix;
    }

    public void setBearerPrefix(String bearerPrefix) {
        this.bearerPrefix = bearerPrefix;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }
}
