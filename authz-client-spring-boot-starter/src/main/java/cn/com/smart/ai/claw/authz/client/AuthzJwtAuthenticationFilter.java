package cn.com.smart.ai.claw.authz.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthzJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthzClientProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, PublicKey> jwksCache = new ConcurrentHashMap<>();
    private final PublicKey configuredPublicKey;

    public AuthzJwtAuthenticationFilter(AuthzClientProperties properties) {
        this.properties = properties;
        this.configuredPublicKey = hasText(properties.getPublicKey())
                ? AuthzRsaKeyUtils.readPublicKey(properties.getPublicKey())
                : null;
        if (configuredPublicKey != null) {
            AuthzRsaKeyUtils.requireRsaPublicKey(configuredPublicKey);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(properties.getBearerPrefix())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(properties.getBearerPrefix().length());
              PublicKey publicKey = resolvePublicKey(token);
            Claims claims = Jwts.parser()
                      .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            List<SimpleGrantedAuthority> authorities = readAuthorities(claims).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
          } catch (JwtException | IllegalArgumentException | IllegalStateException ex) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private PublicKey resolvePublicKey(String token) {
        if (configuredPublicKey != null) {
            return configuredPublicKey;
        }
        if (!hasText(properties.getJwksUri())) {
            throw new IllegalStateException("authz.client.public-key or authz.client.jwks-uri must be configured");
        }
        String keyId = AuthzRsaKeyUtils.readKeyId(token);
        String cacheKey = hasText(keyId) ? keyId : "__default__";
        return jwksCache.computeIfAbsent(cacheKey, ignored -> fetchPublicKeyFromJwks(keyId));
    }

    private PublicKey fetchPublicKeyFromJwks(String keyId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getJwksUri()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Authz JWKS endpoint returned HTTP " + response.statusCode());
            }
            JsonNode keys = objectMapper.readTree(response.body()).get("keys");
            if (keys == null || !keys.isArray()) {
                throw new IllegalStateException("Authz JWKS response does not contain keys array");
            }
            for (JsonNode key : keys) {
                JsonNode kid = key.get("kid");
                boolean matched = hasText(keyId) ? kid != null && keyId.equals(kid.asText()) : true;
                if (matched && "RSA".equals(key.path("kty").asText())) {
                    return AuthzRsaKeyUtils.readPublicKeyFromJwk(key);
                }
            }
            throw new IllegalStateException("No matching RSA key found from Authz JWKS");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to fetch Authz JWKS", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching Authz JWKS", ex);
        }
    }

    private Collection<String> readAuthorities(Claims claims) {
        Object value = claims.get(properties.getAuthoritiesClaim());
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split(","));
        }
        return List.of();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
