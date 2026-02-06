package danjel.votingbackend.controller;
import danjel.votingbackend.dto.FaceVerificationRequest;
import danjel.votingbackend.dto.FaceVerificationResponse;
import danjel.votingbackend.dto.VerificationResponse;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.BlockchainService;
import danjel.votingbackend.service.FaceRecognitionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/verification")
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

    @PostMapping("/face/enroll")
    public ResponseEntity<FaceVerificationResponse> enrollFace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FaceVerificationRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        FaceVerificationResponse response = faceRecognitionService.enrollFace(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/face/verify")
    public ResponseEntity<FaceVerificationResponse> verifyFace(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FaceVerificationRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        FaceVerificationResponse response = faceRecognitionService.verifyFace(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

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

    @GetMapping("/vote/{voteHash}")
    public ResponseEntity<VerificationResponse> verifyVote(@PathVariable String voteHash) {
        VerificationResponse response = blockchainService.verifyVote(voteHash);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vote/receipt/{receiptToken}")
    public ResponseEntity<VerificationResponse> verifyByReceipt(@PathVariable String receiptToken) {
        // In production, would look up vote by receipt token
        return ResponseEntity.ok(VerificationResponse.failure("Receipt verification not yet implemented"));
    }

    // ==================== Blockchain Verification ====================

    @GetMapping("/blockchain/{electionId}/validate")
    public ResponseEntity<Map<String, Object>> validateBlockchain(@PathVariable String electionId) {
        boolean isValid = blockchainService.validateChain(electionId);

        Map<String, Object> response = new HashMap<>();
        response.put("electionId", electionId);
        response.put("chainValid", isValid);
        response.put("blockCount", blockchainService.getBlockCount(electionId));
        response.put("totalTransactions", blockchainService.getTotalTransactions(electionId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/blockchain/{electionId}/stats")
    public ResponseEntity<Map<String, Object>> getBlockchainStats(@PathVariable String electionId) {
        Map<String, Object> response = new HashMap<>();
        response.put("electionId", electionId);
        response.put("blockCount", blockchainService.getBlockCount(electionId));
        response.put("totalTransactions", blockchainService.getTotalTransactions(electionId));
        response.put("chainValid", blockchainService.validateChain(electionId));

        return ResponseEntity.ok(response);
    }

    // ==================== Voter Identity Verification ====================

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

    @PostMapping("/admin/verify-voter/{voterId}")
    public ResponseEntity<Map<String, Object>> adminVerifyVoter(
            @PathVariable String voterId,
            @AuthenticationPrincipal UserDetails userDetails) {

        authService.verifyVoter(voterId);

        Map<String, Object> response = new HashMap<>();
        response.put("voterId", voterId);
        response.put("verified", true);
        response.put("verifiedBy", userDetails.getUsername());

        return ResponseEntity.ok(response);
    }
}