package danjel.votingbackend.controller;

import danjel.votingbackend.dto.FaceVerificationRequest;
import danjel.votingbackend.dto.FaceVerificationResponse;
import danjel.votingbackend.dto.VerificationResponse;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.BlockchainService;
import danjel.votingbackend.service.FaceRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/verification")
@Tag(name = "Verification", description = "Face verification and vote verification")
@SecurityRequirement(name = "Bearer Authentication")
public class VerificationController {

    private final FaceRecognitionService faceRecognitionService;
    private final BlockchainService blockchainService;
    private final AuthService authService;

    public VerificationController(FaceRecognitionService faceRecognitionService,
                                  BlockchainService blockchainService,
                                  AuthService authService) {
        this.faceRecognitionService = faceRecognitionService;
        this.blockchainService = blockchainService;
        this.authService = authService;
    }

    // ==================== Face Verification ====================

    @Operation(
            summary = "Enroll face for voter",
            description = "Enrolls the voter's face for biometric verification. Required before voting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Face enrolled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid image or no face detected"),
            @ApiResponse(responseCode = "409", description = "Face already enrolled")
    })
    @PostMapping("/face/enroll")
    public ResponseEntity<FaceVerificationResponse> enrollFace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FaceVerificationRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        FaceVerificationResponse response = faceRecognitionService.enrollFace(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify face",
            description = "Verifies the voter's face against the enrolled biometric data"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification result returned"),
            @ApiResponse(responseCode = "400", description = "No face detected or low quality image"),
            @ApiResponse(responseCode = "412", description = "Face not enrolled yet")
    })
    @PostMapping("/face/verify")
    public ResponseEntity<FaceVerificationResponse> verifyFace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FaceVerificationRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        FaceVerificationResponse response = faceRecognitionService.verifyFace(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get liveness challenge",
            description = "Returns a liveness challenge for anti-spoofing verification"
    )
    @ApiResponse(responseCode = "200", description = "Liveness challenge returned")
    @GetMapping("/face/liveness-challenge")
    public ResponseEntity<Map<String, String>> getLivenessChallenge(
            @AuthenticationPrincipal UserDetails userDetails) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        String challenge = faceRecognitionService.generateLivenessChallenge(voter.getId());

        Map<String, String> response = new HashMap<>();
        response.put("challenge", challenge);
        response.put("instructions", "Please follow the on-screen instructions to complete liveness verification");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get face verification status",
            description = "Checks if the voter has enrolled their face"
    )
    @ApiResponse(responseCode = "200", description = "Face enrollment status")
    @GetMapping("/face/status")
    public ResponseEntity<Map<String, Object>> getFaceVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("enrolled", voter.isFaceVerified());
        response.put("voterId", voter.getId());

        return ResponseEntity.ok(response);
    }

    // ==================== Vote Verification ====================

    @Operation(
            summary = "Verify vote by hash",
            description = "Verifies a vote exists on the blockchain using its hash"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote verification result"),
            @ApiResponse(responseCode = "404", description = "Vote not found")
    })
    @GetMapping("/vote/{voteHash}")
    public ResponseEntity<VerificationResponse> verifyVote(
            @Parameter(description = "Vote hash from vote receipt") @PathVariable String voteHash) {
        VerificationResponse response = blockchainService.verifyVote(voteHash);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify vote by receipt token",
            description = "Verifies a vote using the receipt token provided after voting"
    )
    @GetMapping("/vote/receipt/{receiptToken}")
    public ResponseEntity<VerificationResponse> verifyByReceipt(
            @Parameter(description = "Receipt token from vote confirmation") @PathVariable String receiptToken) {
        return ResponseEntity.ok(VerificationResponse.failure("Receipt verification not yet implemented"));
    }

    // ==================== Blockchain Verification ====================

    @Operation(
            summary = "Validate election blockchain",
            description = "Validates the integrity of the entire blockchain for an election"
    )
    @ApiResponse(responseCode = "200", description = "Blockchain validation result")
    @GetMapping("/blockchain/{electionId}/validate")
    public ResponseEntity<Map<String, Object>> validateBlockchain(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        boolean isValid = blockchainService.validateChain(electionId);

        Map<String, Object> response = new HashMap<>();
        response.put("electionId", electionId);
        response.put("chainValid", isValid);
        response.put("blockCount", blockchainService.getBlockCount(electionId));
        response.put("totalTransactions", blockchainService.getTotalTransactions(electionId));

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get blockchain statistics",
            description = "Returns statistics about the election blockchain"
    )
    @ApiResponse(responseCode = "200", description = "Blockchain statistics")
    @GetMapping("/blockchain/{electionId}/stats")
    public ResponseEntity<Map<String, Object>> getBlockchainStats(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        Map<String, Object> response = new HashMap<>();
        response.put("electionId", electionId);
        response.put("blockCount", blockchainService.getBlockCount(electionId));
        response.put("totalTransactions", blockchainService.getTotalTransactions(electionId));
        response.put("chainValid", blockchainService.validateChain(electionId));

        return ResponseEntity.ok(response);
    }

    // ==================== Voter Identity Verification ====================

    @Operation(
            summary = "Verify voter identity",
            description = "Returns the verification status of the authenticated voter"
    )
    @ApiResponse(responseCode = "200", description = "Voter identity verification status")
    @PostMapping("/identity/verify")
    public ResponseEntity<Map<String, Object>> verifyIdentity(
            @AuthenticationPrincipal UserDetails userDetails) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("voterId", voter.getId());
        response.put("verified", voter.isVerified());
        response.put("faceVerified", voter.isFaceVerified());
        response.put("eligible", voter.isEligibleToVote());
        response.put("county", voter.getCounty().getDisplayName());
        response.put("municipality", voter.getMunicipality().getDisplayName());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Admin: Verify a voter",
            description = "Manually verifies a voter's identity (Admin only)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Voter verified successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only"),
            @ApiResponse(responseCode = "404", description = "Voter not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/verify-voter/{voterId}")
    public ResponseEntity<Map<String, Object>> adminVerifyVoter(
            @Parameter(description = "Voter ID to verify") @PathVariable String voterId,
            @AuthenticationPrincipal UserDetails userDetails) {

        authService.verifyVoter(voterId);

        Map<String, Object> response = new HashMap<>();
        response.put("voterId", voterId);
        response.put("verified", true);
        response.put("verifiedBy", userDetails.getUsername());

        return ResponseEntity.ok(response);
    }
}