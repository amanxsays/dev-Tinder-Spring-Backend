package integration_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceAssertionVerifierTests {
    private static final String SECRET = "isolated-test-key-32-bytes-minimum";
    private static final String USER = "68847cafa68d283cb2be86db";
    private static final String TIME = "1793836800";
    private final AiServiceAssertionVerifier verifier = new AiServiceAssertionVerifier(SECRET, true,
            Clock.fixed(Instant.ofEpochSecond(Long.parseLong(TIME)), ZoneOffset.UTC));

    static String sign(String query, String user, String time) throws Exception {
        var hex = HexFormat.of();
        String digest = hex.formatHex(MessageDigest.getInstance("SHA-256")
                .digest(query.getBytes(StandardCharsets.UTF_8)));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hex.formatHex(mac.doFinal(("POST\n/api/chat/stream\n" + user + "\n" + time + "\n" + digest)
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test void authenticatesUtf8WithoutUsingBrowserSuppliedIdentity() throws Exception {
        String query = "Résumé summary — please\nありがとう";
        // This fixture was independently generated with Node's node:crypto implementation.
        String nodeSignature = "dfe5012966333095c901f7219f1247bbd06038a6a969c7735ef3e387b22e6408";
        assertEquals(nodeSignature, sign(query, USER, TIME));
        assertEquals(USER, verifier.authenticate(query, "forged-user", USER, TIME, nodeSignature));
    }

    @Test void rejectsUnsignedAlteredExpiredAndFutureAssertions() throws Exception {
        assertStatus(401, () -> verifier.authenticate("hi", USER, null, null, null));
        String signature = sign("hi", USER, TIME);
        assertStatus(401, () -> verifier.authenticate("changed", USER, USER, TIME, signature));
        for (long delta : new long[]{-61, 6}) {
            String time = Long.toString(Long.parseLong(TIME) + delta);
            assertStatus(401, () -> verifier.authenticate("hi", USER, USER, time, sign("hi", USER, time)));
        }
    }

    @Test void rejectsInvalidIdentityAndHeaderShapes() throws Exception {
        assertStatus(401, () -> verifier.authenticate("hi", USER, "not-an-id", TIME, "0".repeat(64)));
        assertStatus(401, () -> verifier.authenticate("hi", USER, USER, "-1", "0".repeat(64)));
        assertStatus(401, () -> verifier.authenticate("hi", USER, USER, TIME, "short"));
        assertStatus(401, () -> verifier.authenticate("hi", USER, USER, TIME, "0".repeat(64)));
    }

    @Test void unconfiguredRequiredGatewayFailsClosed() {
        var missing = new AiServiceAssertionVerifier("", true);
        assertStatus(503, () -> missing.authenticate("hello", USER, null, null, null));
    }

    @Test void explicitLegacyWindowNeverAcceptsPartiallySignedRequests() {
        var compatibility = new AiServiceAssertionVerifier(SECRET, false);
        assertEquals(USER, compatibility.authenticate("hello", USER, null, null, null));
        assertStatus(401, () -> compatibility.authenticate("hello", USER, USER, null, null));
    }

    @Test void rejectsInvalidQueryBeforeAnyWork() {
        for (String query : new String[]{null, " ", "x".repeat(16001)}) {
            assertStatus(400, () -> verifier.authenticate(query, USER, null, null, null));
        }
    }

    private static void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}
