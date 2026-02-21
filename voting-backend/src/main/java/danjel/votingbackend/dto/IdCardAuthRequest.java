package danjel.votingbackend.dto;

import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Sent by the Android app after completing the full on-device verification:
 *
 *   1. NFC chip read     → all fields below
 *   2. ML Kit liveness   → livenessConfirmed = true
 *   3. DeepFace /verify  → Android calls Python server directly,
 *                          only continues here if verified = true
 *
 * The Java backend receives ONLY chip data.
 * Face comparison is entirely the Android app's responsibility.
 * No images, no DeepFace scores, no face tokens.
 */
@Getter
@Setter
public class IdCardAuthRequest {

    @NotBlank(message = "National ID is required")
    private String nationalId;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotNull(message = "Card expiry date is required")
    private LocalDate cardExpiryDate;

    @NotNull(message = "County is required")
    private AlbanianCounty county;

    @NotNull(message = "Municipality is required")
    private AlbanianMunicipality municipality;

    /**
     * The Android app only sends this request if ML Kit liveness passed
     * AND DeepFace returned verified=true. This flag is a final confirmation.
     * Backend rejects the request if it is false.
     */
    @NotNull(message = "Verification result is required")
    private Boolean verificationPassed;

    /** Optional: Android device ID for audit logging. */
    private String deviceId;
}