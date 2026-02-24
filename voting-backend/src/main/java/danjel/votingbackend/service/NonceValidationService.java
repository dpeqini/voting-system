package danjel.votingbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * ══════════════════════════════════════════════════════════════
 *  NonceValidationService — Replay Attack Prevention (Backend)
 *
 *  Validates three things on every protected request:
 *    1. TIMESTAMP — request must arrive within a ±60-second window
 *    2. NONCE     — UUID must not have been seen before (cached for 5 minutes)
 *    3. SIGNATURE — HMAC-SHA256 over "nonce:timestamp:path" must verify
 *
 *  The nonce cache uses Caffeine (in-process). For a multi-instance
 *  deployment, replace with Redis so all instances share the same
 *  seen-nonces set — otherwise an attacker can replay to a different node.
 *
 *  Required dependency in pom.xml:
 *    <dependency>
 *        <groupId>com.github.ben-manes.caffeine</groupId>
 *        <artifactId>caffeine</artifactId>
 *    </dependency>
 *
 *  For Redis replace:
 *    private final Cache<String, Boolean> usedNonces = ...
 *  with:
 *    private final RedisTemplate<String, String> redisTemplate;
 *    and use setIfAbsent(nonce, "1", 5, MINUTES)
 * ══════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
public class NonceValidationService {

    // Acceptance window: reject requests older than this, or timestamped in the future
    private static final long MAX_AGE_MS      = 60_000L;  // 60 seconds
    private static final long CLOCK_SKEW_MS   = 5_000L;   // allow 5s clock skew on device

    // Store seen nonces for longer than MAX_AGE_MS so an attacker can't wait for expiry
    private static final long NONCE_CACHE_TTL_MINUTES = 5L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * In-process nonce cache.
     * Key   = nonce string (UUID)
     * Value = Boolean.TRUE (we only care about presence)
     *
     * PRODUCTION NOTE: Replace with Redis for multi-node deployments.
     */
    private final Cache<String, Boolean> usedNonces = Caffeine.newBuilder()
            .expireAfterWrite(NONCE_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(100_000)   // ~100 k concurrent unique voters
            .build();

    /**
     * Validate a signed request.
     *
     * @param nonce             value of X-Request-Nonce header
     * @param timestampStr      value of X-Request-Timestamp header (epoch ms as string)
     * @param path              request path (e.g. "/api/v1/auth/id-card")
     * @param receivedSignature value of X-Request-Signature header
     * @param deviceSecret      the HMAC secret registered for this device
     * @throws ReplayAttackException if any check fails
     */
    public void validate(
            String nonce,
            String timestampStr,
            String path,
            String receivedSignature,
            byte[] deviceSecret) {

        // ── 1. Parse timestamp ───────────────────────────────────────────────────
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("Malformed X-Request-Timestamp: '{}'", timestampStr);
            throw new ReplayAttackException("Invalid timestamp format");
        }

        // ── 2. Check timestamp freshness ─────────────────────────────────────────
        long now = System.currentTimeMillis();
        long age = now - timestamp;

        if (age > MAX_AGE_MS) {
            log.warn("Stale request: age {}ms > {}ms, nonce={}", age, MAX_AGE_MS, nonce);
            throw new ReplayAttackException("Request expired — timestamp is too old");
        }
        if (age < -CLOCK_SKEW_MS) {
            log.warn("Future-dated request: age {}ms, nonce={}", age, nonce);
            throw new ReplayAttackException("Request timestamp is in the future");
        }

        // ── 3. Check nonce uniqueness ─────────────────────────────────────────────
        // putIfAbsent returns null when the key did NOT exist — meaning this is fresh.
        // If it returns non-null the nonce was already seen — replay detected.
        Boolean existing = usedNonces.asMap().putIfAbsent(nonce, Boolean.TRUE);
        if (existing != null) {
            log.warn("REPLAY DETECTED: nonce '{}' has already been used", nonce);
            throw new ReplayAttackException("Nonce already used — replay attack detected");
        }

        // ── 4. Verify HMAC signature ──────────────────────────────────────────────
        String expectedSignature = computeHmac(nonce, timestampStr, path, deviceSecret);
        if (!safeEquals(expectedSignature, receivedSignature)) {
            // Remove nonce from cache so the client can retry with a valid signature
            usedNonces.invalidate(nonce);
            log.warn("Signature mismatch for path={}, nonce={}", path, nonce);
            throw new ReplayAttackException("Request signature verification failed");
        }

        log.debug("Request validated: path={}, nonce={}, age={}ms", path, nonce, age);
    }

    /**
     * Convenience overload that validates without HMAC signature check.
     * Use this during the initial migration period when device secrets are not yet
     * registered. Remove once all clients send valid signatures.
     *
     * @deprecated Migrate to {@link #validate(String, String, String, String, byte[])}
     */
    @Deprecated
    public void validateTimestampAndNonceOnly(String nonce, String timestampStr) {
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new ReplayAttackException("Invalid timestamp format");
        }

        long age = System.currentTimeMillis() - timestamp;
        if (age > MAX_AGE_MS)  throw new ReplayAttackException("Request expired");
        if (age < -CLOCK_SKEW_MS) throw new ReplayAttackException("Timestamp in the future");

        Boolean existing = usedNonces.asMap().putIfAbsent(nonce, Boolean.TRUE);
        if (existing != null) throw new ReplayAttackException("Nonce already used");
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    /**
     * Compute HMAC-SHA256 over "nonce:timestamp:path".
     * Must exactly match what Android's NonceManager.signRequest() produces.
     */
    private String computeHmac(String nonce, String timestamp, String path, byte[] secret) {
        try {
            String message = nonce + ":" + timestamp + ":" + path;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] rawMac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawMac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing side-channel attacks.
     * Do NOT use String.equals() for comparing secrets or signatures.
     */
    private boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= (aBytes[i] ^ bBytes[i]);
        }
        return result == 0;
    }

    // ─── Exception ──────────────────────────────────────────────────────────────

    public static class ReplayAttackException extends RuntimeException {
        public ReplayAttackException(String message) {
            super(message);
        }
    }
}