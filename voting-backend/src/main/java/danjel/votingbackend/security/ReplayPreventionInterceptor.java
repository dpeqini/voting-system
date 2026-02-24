package danjel.votingbackend.security;

import danjel.votingbackend.service.NonceValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  ReplayPreventionInterceptor — Spring HandlerInterceptor
 *
 *  Intercepts every request to protected paths and validates the
 *  anti-replay headers added by the Android NonceManager / ApiClient.
 *
 *  Applies to: all endpoints registered in WebConfig (see below)
 *  Rejects with HTTP 429 Too Many Requests when replay is detected.
 *  Rejects with HTTP 400 Bad Request when headers are missing/malformed.
 *
 *  Registration (WebConfig.java):
 *
 *    @Override
 *    public void addInterceptors(InterceptorRegistry registry) {
 *        registry.addInterceptor(replayPreventionInterceptor)
 *                .addPathPatterns("/api/v1/auth/**", "/api/v1/vote/**");
 *    }
 * ══════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayPreventionInterceptor implements HandlerInterceptor {

    private static final String HEADER_NONCE     = "X-Request-Nonce";
    private static final String HEADER_TIMESTAMP = "X-Request-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Request-Signature";
    private static final List<String> ORIGINS_TO_IGNORE = List.of("http://localhost:4200");
    private final NonceValidationService nonceValidationService;
    private final DeviceSecretRegistry   deviceSecretRegistry;

    @Override
    public boolean preHandle(
            HttpServletRequest  request,
            HttpServletResponse response,
            Object              handler) throws IOException {

        String nonce     = request.getHeader(HEADER_NONCE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (request.getHeader("Origin") != null && ORIGINS_TO_IGNORE.contains(request.getHeader("Origin"))) {
            return true;
        }
        // ── 1. Require all three headers ─────────────────────────────────────────
        if (nonce == null || timestamp == null || signature == null) {
            log.warn("Missing anti-replay headers on {} {} — rejecting",
                    request.getMethod(), request.getRequestURI());
            writeError(response, HttpStatus.BAD_REQUEST,
                    "Missing required security headers: X-Request-Nonce, X-Request-Timestamp, X-Request-Signature");
            return false;
        }

        // ── 2. Look up the device secret for HMAC verification ───────────────────
        // During the auth endpoint the device may not yet have a registered secret —
        // use the overloaded validateTimestampAndNonceOnly() for /auth/id-card.
        // For all voting endpoints a registered secret is required.
        String path = request.getRequestURI();
        byte[] deviceSecret = deviceSecretRegistry.getSecretForRequest(request);

        // ── 3. Validate nonce + timestamp (+ HMAC if secret available) ────────────
        try {
            if (deviceSecret != null) {
                nonceValidationService.validate(nonce, timestamp, path, signature, deviceSecret);
            } else {
                // Migration mode: timestamp + nonce only, no HMAC verification yet
                // TODO: Remove this branch once all clients register device secrets
                log.debug("No device secret registered; validating timestamp+nonce only for {}", path);
                nonceValidationService.validateTimestampAndNonceOnly(nonce, timestamp);
            }
        } catch (NonceValidationService.ReplayAttackException e) {
            log.warn("Replay prevention triggered on {} {}: {}", request.getMethod(), path, e.getMessage());
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
            return false;
        }

        log.info("Replay success prevention triggered on {} {}: {}", request.getMethod(), path, nonce);
        return true;  // all checks passed — continue to controller
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}"
        );
    }
}
