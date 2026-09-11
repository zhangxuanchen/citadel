package cn.com.app.security.api.dto;

import java.util.List;

public record AuthzDiscoveryResponse(
        String issuer,
        String tokenType,
        String authorizationHeader,
        String signingAlgorithm,
        String jwksEndpoint,
        String userinfoEndpoint,
        List<String> managementEndpoints
) {
}
