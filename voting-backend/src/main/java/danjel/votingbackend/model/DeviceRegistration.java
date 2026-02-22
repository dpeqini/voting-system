package danjel.votingbackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists per-device HMAC secrets so the backend can verify
 * X-Request-Signature on every request from a registered device.
 *
 * SQL (run once in your migration / schema.sql):
 *
 *   CREATE TABLE device_registrations (
 *       device_id     VARCHAR(64)  PRIMARY KEY,
 *       hmac_secret   BYTEA        NOT NULL,
 *       voter_id      UUID         REFERENCES voters(id) ON DELETE CASCADE,
 *       registered_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *       last_seen_at  TIMESTAMPTZ
 *   );
 */
@Entity
@Table(name = "device_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceRegistration {

    @Id
    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    /**
     * 32-byte HMAC-SHA256 key generated on the Android device and stored
     * in Android Keystore. Sent once during the first successful auth.
     */
    @Column(name = "hmac_secret", nullable = false)
    private byte[] hmacSecret;

    /**
     * The voter this device belongs to — set after successful ID-card + face auth.
     * Nullable during the brief window between device registration and auth completion.
     */
    @Column(name = "voter_id")
    private UUID voterId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @PrePersist
    private void prePersist() {
        if (registeredAt == null) registeredAt = Instant.now();
    }
}
