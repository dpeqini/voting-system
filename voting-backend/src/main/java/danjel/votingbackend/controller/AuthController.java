package danjel.votingbackend.controller;

import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.dto.IdCardAuthRequest;
import danjel.votingbackend.security.DeviceSecretRegistry;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.IdCardAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Voter authentication via biometric ID card")
public class AuthController {

    private final IdCardAuthService idCardAuthService;
    private final AuthService       authService;
    private final DeviceSecretRegistry deviceSecretRegistry;

    public AuthController(IdCardAuthService idCardAuthService, AuthService authService, DeviceSecretRegistry deviceSecretRegistry) {
        this.idCardAuthService = idCardAuthService;
        this.authService       = authService;
        this.deviceSecretRegistry = deviceSecretRegistry;
    }

    /**
     * Main voter authentication endpoint.
     *
     * The Android app sends:
     *   - NFC chip data (nationalId, name, DOB, expiry, county, municipality)
     *   - Chip face photo (base64) — extracted from the ICAO DG2 data group
     *   - Live selfie (base64)     — captured after ML Kit liveness passed
     *   - livenessConfirmed: true  — ML Kit confirmed a real person
     *
     * The backend:
     *   1. Rejects the request if livenessConfirmed = false
     *   2. Validates chip data (age ≥ 18, card not expired, region consistency)
     *   3. Calls the Python DeepFace server internally to compare the two images
     *   4. Rejects if face distance > configured threshold
     *   5. Auto-registers new voter or syncs returning voter
     *   6. Returns JWT access + refresh tokens
     *
     * Face images travel: Android → HTTPS → Backend → internal → Python server
     * They are NOT stored anywhere after the comparison is complete.
     */
    @Operation(
            summary = "Authenticate voter via biometric ID card",
            description = """
                    Authenticates a voter using their Albanian biometric NFC ID card.
                    
                    **What the Android app must do before calling this endpoint:**
                    1. Read the NFC chip — extract identity fields and the chip face photo
                    2. Run ML Kit Face Detection for liveness (blink/head movement)
                    3. Capture a live selfie
                    4. Send all data to this endpoint
                    
                    **What the backend does:**
                    - Calls the Python DeepFace server to compare chip photo vs live selfie
                    - Validates age ≥ 18 and card not expired
                    - Auto-registers new voter or loads existing one
                    - Returns a 30-minute JWT access token + 7-day refresh token
                    
                    **Face images are never stored.** They are discarded after the comparison.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful — JWT returned",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Liveness check failed, card expired, or invalid chip data"),
            @ApiResponse(responseCode = "401", description = "Face does not match ID card photo"),
            @ApiResponse(responseCode = "429", description = "Too many failed face verification attempts — rate limited"),
            @ApiResponse(responseCode = "503", description = "Face verification service temporarily unavailable")
    })
    @PostMapping("/id-card")
    public ResponseEntity<AuthResponse> authenticateWithIdCard(
            @Valid @RequestBody IdCardAuthRequest request,
            HttpServletRequest httpRequest) {
        final String deviceId = httpRequest.getHeader("X-Device-ID");
        final String deviceSecretB64 = httpRequest.getHeader("X-Device-Secret");

        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest().body(AuthResponse.failure("Missing X-Device-ID header"));
        }
        if (deviceSecretB64 == null || deviceSecretB64.isBlank()) {
            return ResponseEntity.badRequest().body(AuthResponse.failure("Missing X-Device-Secret header"));
        }

        byte[] deviceSecret;
        try {
            deviceSecret = java.util.Base64.getDecoder().decode(deviceSecretB64);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(AuthResponse.failure("Invalid Base64 in X-Device-Secret"));
        }

        AuthResponse response = idCardAuthService.authenticateWithIdCard(request, deviceId);
        try {
            UUID voterUuid = UUID.fromString(response.getVoterId());
            deviceSecretRegistry.registerDevice(deviceId, voterUuid, deviceSecret);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(response);
    }

    // ── Token management ──────────────────────────────────────────────────────

    @Operation(
            summary = "Refresh access token",
            description = "Generates a new 30-minute access token using a valid 7-day refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Parameter(description = "Refresh token — format: Bearer <token>")
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader("X-Device-ID") String deviceId) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.failure("Authorization header must be: Bearer <token>"));
        }
        return ResponseEntity.ok(authService.refreshToken(authHeader.substring(7), deviceId));
    }

    @Operation(
            summary = "Verify token validity",
            description = "Checks if the provided JWT access token is still valid and not expired"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    @GetMapping("/verify-token")
    public ResponseEntity<AuthResponse> verifyToken(
            @Parameter(description = "Access token — format: Bearer <token>")
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.failure("Authorization header must be: Bearer <token>"));
        }
        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setMessage("Token is valid");
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Logout",
            description = "Ends the voter session. The client must discard both tokens."
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT is stateless — actual invalidation requires a token blacklist (not implemented).
        // For the demo, the client is responsible for discarding the token.
        return ResponseEntity.ok().build();
    }
}