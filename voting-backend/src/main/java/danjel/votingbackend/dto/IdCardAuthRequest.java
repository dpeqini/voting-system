package danjel.votingbackend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Sent by the Android app to authenticate a voter via biometric ID card.
 *
 * The Android is responsible for:
 *   1. NFC chip read  →  all chip fields below + chipFaceImageBase64
 *   2. ML Kit         →  liveness confirmed on-device + live selfie captured
 *
 * The BACKEND is responsible for:
 *   3. Calling the Python DeepFace server to compare chipFaceImageBase64 vs liveFaceImageBase64
 *   4. Validating the result and issuing a JWT on success
 *
 * Face images travel Android → Backend (HTTPS) → Python (internal).
 * They never hit the public internet on the second hop.
 */
@Getter
@Setter
public class IdCardAuthRequest {

    // ── Chip identity fields ──────────────────────────────────────────────────

    @NotBlank(message = "National ID is required")
    private String nationalId;

    @NotBlank(message = "First name is required")
    private String name;

    @NotNull(message = "Date of birth is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @NotNull(message = "Card expiry date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private AlbanianCounty county;

    @NotNull(message = "Municipality is required")
    private AlbanianMunicipality municipality;

    // ── Biometric images (processed server-side) ──────────────────────────────
    @NotBlank(message = "Device public key is required")
    private String devicePublicKey; // Base64 encoded public key
    /**
     * Face photo extracted from the NFC chip's DG2 data group (base64).
     * This is the government-issued reference photo.
     */
    @NotBlank(message = "Chip face image is required")
    private String chipFacePhoto;

    /**
     * Live selfie captured by the Android camera during the session (base64).
     * ML Kit liveness check must have passed before this is sent.
     */
    @NotBlank(message = "Live face image is required")
    private String liveSelfie;

    // ── Liveness confirmation ─────────────────────────────────────────────────

    /**
     * True if ML Kit Face Detection confirmed the selfie shows a live person.
     * The backend rejects the request immediately if this is false —
     * there is no point calling DeepFace if the liveness check failed.
     */
    @NotNull(message = "Liveness confirmation is required")
    private Boolean livenessConfirmed;

    // ── Optional metadata ─────────────────────────────────────────────────────

    /** Android device fingerprint — stored for audit logging only. */
    private String deviceId;
}