package br.com.cactus.guard.integration.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;

/**
 * OAuth2 de conta de servico Google (JWT bearer, RS256) sem SDK: assina a assertion com a chave do
 * JSON de credenciais e troca por access token (escopo firebase.messaging). Cache ate 60s antes de expirar.
 * Nada de credencial e logado.
 */
@Slf4j
public class FcmAccessTokenProvider implements Supplier<String> {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final String clientEmail;
    private final PrivateKey signingKey;
    private final String tokenUri;
    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    public FcmAccessTokenProvider(String credentialsPath) {
        try {
            JsonNode json = mapper.readTree(Files.readString(Path.of(credentialsPath), StandardCharsets.UTF_8));
            this.clientEmail = json.get("client_email").asString();
            this.tokenUri = json.has("token_uri") ? json.get("token_uri").asString() : "https://oauth2.googleapis.com/token";
            // remove as linhas de delimitacao do PEM e espacos, ficando so o Base64 PKCS#8
            String pem = json.get("private_key").asString()
                    .replaceAll("-----[A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            this.signingKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("FCM_CREDENTIALS_JSON invalido ou ilegivel (" + e.getClass().getSimpleName() + ")");
        }
    }

    @Override
    public synchronized String get() {
        if (cached != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return cached;
        }
        try {
            long now = Instant.now().getEpochSecond();
            String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            String claims = b64(mapper.writeValueAsString(Map.of(
                    "iss", clientEmail, "scope", SCOPE, "aud", tokenUri, "iat", now, "exp", now + 3600)));
            String unsigned = header + "." + claims;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(signingKey);
            sig.update(unsigned.getBytes(StandardCharsets.UTF_8));
            String assertion = unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion);
            JsonNode resp = http.post().uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            cached = resp.get("access_token").asString();
            long ttl = resp.has("expires_in") ? resp.get("expires_in").asLong() : 3600;
            expiresAt = Instant.now().plusSeconds(ttl);
            return cached;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter access token FCM (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
