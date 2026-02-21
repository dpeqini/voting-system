package danjel.votingbackend.controller;

import danjel.votingbackend.dto.VerificationResponse;
import danjel.votingbackend.service.BlockchainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Vote and blockchain verification endpoints.
 *
 * Face verification has NO endpoints here.
 * The entire biometric pipeline runs on the Android app before authentication:
 *   1. NFC chip read
 *   2. ML Kit liveness detection
 *   3. DeepFace /verify (Android → Python server directly)
 *   4. POST /api/v1/auth/id-card (chip data only) → JWT issued
 *
 * By the time a voter holds a JWT there is nothing left to confirm about their
 * identity. The face/confirmation/token round-trip is eliminated entirely.
 */
@RestController
@RequestMapping("/api/v1/verification")
@Tag(name = "Verification", description = "Vote and blockchain verification")
@SecurityRequirement(name = "Bearer Authentication")
public class VerificationController {

    private final BlockchainService blockchainService;

    public VerificationController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    // ── Vote verification ─────────────────────────────────────────────────────

    @Operation(
            summary = "Verify vote by hash",
            description = "Verifies a vote exists on the blockchain using its hash. " +
                    "Voters use this after casting to confirm their vote was recorded."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote verification result"),
            @ApiResponse(responseCode = "404", description = "Vote not found")
    })
    @GetMapping("/vote/{voteHash}")
    public ResponseEntity<VerificationResponse> verifyVote(
            @Parameter(description = "Vote hash from the receipt screen")
            @PathVariable String voteHash) {
        return ResponseEntity.ok(blockchainService.verifyVote(voteHash));
    }

    @Operation(
            summary = "Verify vote by receipt token",
            description = "Verifies a vote using the full receipt token from the confirmation screen"
    )
    @GetMapping("/vote/receipt/{receiptToken}")
    public ResponseEntity<VerificationResponse> verifyByReceipt(
            @Parameter(description = "Receipt token from vote confirmation screen")
            @PathVariable String receiptToken) {
        return ResponseEntity.ok(blockchainService.verifyVoteByReceipt(receiptToken));
    }

    // ── Blockchain verification ───────────────────────────────────────────────

    @Operation(
            summary = "Validate election blockchain",
            description = "Validates the full integrity of the blockchain for a given election"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation result"),
            @ApiResponse(responseCode = "404", description = "Election not found")
    })
    @GetMapping("/blockchain/{electionId}/validate")
    public ResponseEntity<Map<String, Object>> validateBlockchain(
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        return ResponseEntity.ok(Map.of(
                "electionId",        electionId,
                "chainValid",        blockchainService.validateChain(electionId),
                "blockCount",        blockchainService.getBlockCount(electionId),
                "totalTransactions", blockchainService.getTotalTransactions(electionId)
        ));
    }

    @Operation(
            summary = "Get blockchain statistics",
            description = "Returns block count, transaction count, and chain validity for an election"
    )
    @GetMapping("/blockchain/{electionId}/stats")
    public ResponseEntity<Map<String, Object>> getBlockchainStats(
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        return ResponseEntity.ok(Map.of(
                "electionId",        electionId,
                "blockCount",        blockchainService.getBlockCount(electionId),
                "totalTransactions", blockchainService.getTotalTransactions(electionId),
                "chainValid",        blockchainService.validateChain(electionId)
        ));
    }
}