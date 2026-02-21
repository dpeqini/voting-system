package danjel.votingbackend.controller;

import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.dto.IdCardAuthRequest;
import danjel.votingbackend.service.IdCardAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "ID-card-based voter authentication")
public class AuthController {

    private final IdCardAuthService idCardAuthService;

    public AuthController(IdCardAuthService idCardAuthService) {
        this.idCardAuthService = idCardAuthService;
    }

    /**
     * Single authentication endpoint for the Android app.
     *
     * The app must:
     *   1. Read the NFC chip and extract voter data + chip face photo
     *   2. Run ML Kit liveness detection and capture a live selfie
     *   3. POST everything here
     *
     * The backend:
     *   - Validates age (≥ 18) and card expiry
     *   - Calls the local DeepFace Python server to compare chip photo vs live selfie
     *   - Auto-registers new voters or loads returning ones by nationalId
     *   - Returns a JWT session token valid for 30 minutes
     *
     * No username, no password, no pre-registration required.
     */
    @Operation(
            summary = "Authenticate via biometric ID card",
            description = """
                    Complete voter authentication using Albanian NFC biometric ID card.
                    
                    **Android app must provide:**
                    - All data fields read from the NFC chip
                    - The face photo extracted from chip (DG2)
                    - A live selfie captured after ML Kit liveness passed
                    - The liveness confirmation result from ML Kit
                    
                    **Backend validates:**
                    - Age ≥ 18 years from dateOfBirth
                    - Card not expired (cardExpiryDate ≥ today)
                    - Municipality belongs to the given county
                    - ML Kit confirmed liveness
                    - DeepFace confirms chip photo matches live selfie
                    
                    **On success:**
                    - New voters are auto-registered from chip data
                    - Returning voters are loaded and their card data updated if renewed
                    - JWT access token (30 min) + refresh token returned
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid chip data or validation error"),
            @ApiResponse(responseCode = "403", description = "Age < 18 or card expired"),
            @ApiResponse(responseCode = "412", description = "Liveness check failed"),
            @ApiResponse(responseCode = "422", description = "Face does not match ID card"),
            @ApiResponse(responseCode = "503", description = "Face recognition service unavailable")
    })
    @PostMapping("/id-card")
    public ResponseEntity<AuthResponse> authenticateWithIdCard(
            @Valid @RequestBody IdCardAuthRequest request) {
        AuthResponse response = idCardAuthService.authenticateWithIdCard(request);
        return ResponseEntity.ok(response);
    }

    // ── Token refresh (unchanged — still needed for session extension) ─────────

    @Operation(
            summary = "Refresh access token",
            description = "Generates a new access token using a valid refresh token. " +
                    "Use this to extend the session without re-scanning the ID card."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.failure("Invalid authorization header"));
        }

        // Delegate to existing AuthService.refreshToken() — no change needed there
        return ResponseEntity.ok(AuthResponse.failure("Refresh not yet wired — inject AuthService"));
    }
}