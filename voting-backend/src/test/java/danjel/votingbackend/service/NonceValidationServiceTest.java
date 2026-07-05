package danjel.votingbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for replay-attack prevention (NonceValidationService), Kreu VI/VII.
 *
 * Covers the three independent checks performed on every signed request:
 *   1. timestamp freshness (±60s window, 5s clock skew)
 *   2. nonce uniqueness (a nonce may be used only once)
 *   3. HMAC-SHA256 signature over "nonce:timestamp:path"
 */
class NonceValidationServiceTest {

    private NonceValidationService service;

    private static final String PATH = "/api/v1/vote";
    private static final byte[] SECRET =
            "device-hmac-secret-0123456789".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        service = new NonceValidationService();
    }

    /** Mirrors the server's private computeHmac so tests can produce a valid signature. */
    private String sign(String nonce, String timestamp, String path, byte[] secret) throws Exception {
        String message = nonce + ":" + timestamp + ":" + path;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    private String freshTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Test
    @DisplayName("A fresh, correctly-signed request passes validation")
    void validRequestPasses() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String ts = freshTimestamp();
        String sig = sign(nonce, ts, PATH, SECRET);

        assertThatCode(() -> service.validate(nonce, ts, PATH, sig, SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Reusing a nonce is rejected as a replay attack")
    void replayedNonceIsRejected() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String ts = freshTimestamp();
        String sig = sign(nonce, ts, PATH, SECRET);

        service.validate(nonce, ts, PATH, sig, SECRET);   // first use — ok

        assertThatThrownBy(() -> service.validate(nonce, ts, PATH, sig, SECRET))
                .isInstanceOf(NonceValidationService.ReplayAttackException.class)
                .hasMessageContaining("Nonce already used");
    }

    @Test
    @DisplayName("A stale timestamp (older than the window) is rejected")
    void staleTimestampIsRejected() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis() - 120_000L); // 2 min old
        String sig = sign(nonce, ts, PATH, SECRET);

        assertThatThrownBy(() -> service.validate(nonce, ts, PATH, sig, SECRET))
                .isInstanceOf(NonceValidationService.ReplayAttackException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("A future-dated timestamp beyond clock skew is rejected")
    void futureTimestampIsRejected() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis() + 60_000L); // 1 min ahead
        String sig = sign(nonce, ts, PATH, SECRET);

        assertThatThrownBy(() -> service.validate(nonce, ts, PATH, sig, SECRET))
                .isInstanceOf(NonceValidationService.ReplayAttackException.class);
    }

    @Test
    @DisplayName("A wrong HMAC signature is rejected, and the nonce is freed for retry")
    void wrongSignatureIsRejectedAndNonceReleased() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String ts = freshTimestamp();
        String wrongSig = sign(nonce, ts, PATH, "attacker-guessed-secret".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.validate(nonce, ts, PATH, wrongSig, SECRET))
                .isInstanceOf(NonceValidationService.ReplayAttackException.class)
                .hasMessageContaining("signature");

        // The failed attempt must NOT consume the nonce — a legitimate retry succeeds.
        String goodSig = sign(nonce, ts, PATH, SECRET);
        assertThatCode(() -> service.validate(nonce, ts, PATH, goodSig, SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A malformed timestamp is rejected")
    void malformedTimestampIsRejected() {
        String nonce = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.validate(nonce, "not-a-number", PATH, "sig", SECRET))
                .isInstanceOf(NonceValidationService.ReplayAttackException.class)
                .hasMessageContaining("Invalid timestamp");
    }
}
