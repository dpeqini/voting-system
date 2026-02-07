package danjel.votingbackend.controller;

import danjel.votingbackend.dto.AdminAuthResponse;
import danjel.votingbackend.dto.AdminRegisterRequest;
import danjel.votingbackend.dto.AuthRequest;
import danjel.votingbackend.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin Authentication", description = "Admin registration, login, and token management")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Operation(
            summary = "Admin login",
            description = "Authenticates an admin user and returns JWT access and refresh tokens"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AdminAuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account locked due to too many failed attempts")
    })
    @PostMapping("/login")
    public ResponseEntity<AdminAuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AdminAuthResponse response = adminAuthService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Register a new admin",
            description = "Creates a new admin account. Requires ADMIN role."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(schema = @Schema(implementation = AdminAuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation error"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    public ResponseEntity<AdminAuthResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        AdminAuthResponse response = adminAuthService.register(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Refresh admin access token",
            description = "Generates a new access token using a valid refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AdminAuthResponse> refreshToken(
            @Parameter(description = "Refresh token in format: Bearer <token>")
            @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(AdminAuthResponse.failure("Invalid token"));
        }

        String refreshToken = authHeader.substring(7);
        AdminAuthResponse response = adminAuthService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
}
