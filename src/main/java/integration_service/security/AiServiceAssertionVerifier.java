package integration_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/** Authenticates the Node gateway, not a browser-controlled user ID. */
@Component
public class AiServiceAssertionVerifier {
    private final byte[] secret;
    private final boolean required;
    private final Clock clock;

    @Autowired
    public AiServiceAssertionVerifier(@Value("${ai.gateway.secret:}") String secret,
                                      @Value("${ai.gateway.required:true}") boolean required) {
        this(secret, required, Clock.systemUTC());
    }

    AiServiceAssertionVerifier(String secret, boolean required, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.required = required;
        this.clock = clock;
    }

    public String authenticate(String query, String legacyUserId, String userId,
                               String timestamp, String signature) {
        if (query == null || query.isBlank() || query.length() > 16000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AI query");
        }
        boolean unsigned = userId == null && timestamp == null && signature == null;
        // Only an explicitly configured, temporary deployment compatibility window permits this.
        if (!required && unsigned) return legacyUserId;
        if (secret.length < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI gateway authentication is not configured");
        }
        if (userId == null || !userId.matches("[a-f0-9]{24}")
                || timestamp == null || !timestamp.matches("[0-9]{1,12}")
                || signature == null || !signature.matches("[a-f0-9]{64}")) {
            throw unauthorized();
        }
        try {
            long issued = Long.parseLong(timestamp);
            long current = clock.instant().getEpochSecond();
            if (issued < current - 60 || issued > current + 5) throw unauthorized();
            String queryDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(query.getBytes(StandardCharsets.UTF_8)));
            String canonical = "POST\n/api/chat/stream\n" + userId + "\n" + timestamp + "\n" + queryDigest;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) throw unauthorized();
            return userId;
        } catch (GeneralSecurityException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI gateway authentication is unavailable");
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid AI gateway assertion");
    }
}
