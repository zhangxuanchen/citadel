package cn.com.smart.ai.claw.authz.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class AuthzRsaKeyUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AuthzRsaKeyUtils() {
    }

    static PublicKey readPublicKey(String pem) {
        try {
            String normalized = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid authz.client.public-key configuration", ex);
        }
    }

    static PublicKey readPublicKeyFromJwk(JsonNode jwk) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n").asText()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e").asText()));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA JWK from Citadel", ex);
        }
    }

    static String readKeyId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] header = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode json = OBJECT_MAPPER.readTree(header);
            JsonNode kid = json.get("kid");
            return kid == null || kid.asText().isBlank() ? null : kid.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    static void requireRsaPublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof RSAPublicKey)) {
            throw new IllegalStateException("Authz client public key must be an RSA public key");
        }
    }
}
