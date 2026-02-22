package danjel.votingbackend.security;

import danjel.votingbackend.model.DeviceRegistration;
import danjel.votingbackend.repository.DeviceRegistrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  DeviceSecretRegistry
 *
 *  Resolves the HMAC signing secret for the device that sent the current
 *  request. Used by ReplayPreventionInterceptor to verify X-Request-Signature.
 *
 *  The secret is a 32-byte random value generated on the device (or server-
 *  assigned at first install) and stored server-side keyed by device ID.
 *
 *  In the initial auth flow (before JWT is issued), device identity comes from
 *  the X-Device-ID header. After authentication, it's extracted from the JWT.
 *
 *  Schema (add to your voters / devices table):
 *
 *    CREATE TABLE device_registrations (
 *        device_id     VARCHAR(64) PRIMARY KEY,
 *        hmac_secret   BYTEA       NOT NULL,
 *        voter_id      UUID        REFERENCES voters(id),
 *        registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *        last_seen_at  TIMESTAMPTZ
 *    );
 * ══════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSecretRegistry {

    private static final String HEADER_DEVICE_ID = "X-Device-ID";

    private final DeviceRegistrationRepository deviceRegistrationRepository;

    /**
     * Look up the HMAC secret for the device that sent this request.
     *
     * Returns null if the device has not yet registered a secret — the interceptor
     * will fall back to timestamp+nonce-only validation in that case.
     */
    public byte[] getSecretForRequest(HttpServletRequest request) {
        String deviceId = resolveDeviceId(request);
        if (deviceId == null) return null;

        return deviceRegistrationRepository
                .findByDeviceId(deviceId)
                .map(DeviceRegistration::getHmacSecret)
                .orElse(null);
    }

    /**
     * Register a new device secret. Called from the authentication controller
     * after a successful ID-card + face verification.
     *
     * @param deviceId   value from X-Device-ID header
     * @param voterId    the authenticated voter's UUID
     * @param hmacSecret 32 random bytes from the device
     */
    public void registerDevice(String deviceId, java.util.UUID voterId, byte[] hmacSecret) {
        DeviceRegistration registration = deviceRegistrationRepository
                .findByDeviceId(deviceId)
                .orElse(new DeviceRegistration());

        registration.setDeviceId(deviceId);
        registration.setVoterId(voterId);
        registration.setHmacSecret(hmacSecret);
        registration.setRegisteredAt(java.time.Instant.now());

        deviceRegistrationRepository.save(registration);
        log.info("Device registered: deviceId={}, voterId={}", deviceId, voterId);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    /**
     * Device ID resolution order:
     *   1. JWT claim (preferred — authenticated and tamper-evident)
     *   2. X-Device-ID header (used for unauthenticated requests like /auth/id-card)
     */
    private String resolveDeviceId(HttpServletRequest request) {
        // Try to get device ID from JWT claim first (most requests after login)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String deviceId = jwtAuth.getDeviceId();
            if (deviceId != null) return deviceId;
        }

        // Fall back to header for pre-auth requests
        return request.getHeader(HEADER_DEVICE_ID);
    }
}
