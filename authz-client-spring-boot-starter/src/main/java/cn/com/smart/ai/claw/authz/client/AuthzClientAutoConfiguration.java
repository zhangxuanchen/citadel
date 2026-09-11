package cn.com.smart.ai.claw.authz.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthzClientProperties.class)
@ConditionalOnProperty(prefix = "authz.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthzClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthzJwtAuthenticationFilter authzJwtAuthenticationFilter(AuthzClientProperties properties) {
          boolean hasPublicKey = properties.getPublicKey() != null && !properties.getPublicKey().isBlank();
          boolean hasJwksUri = properties.getJwksUri() != null && !properties.getJwksUri().isBlank();
          if (!hasPublicKey && !hasJwksUri) {
              throw new IllegalStateException("authz.client.public-key or authz.client.jwks-uri must be configured");
        }
        return new AuthzJwtAuthenticationFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain authzClientSecurityFilterChain(
            HttpSecurity http,
            AuthzClientProperties properties,
            AuthzJwtAuthenticationFilter authzJwtAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.getPermitPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authzJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
