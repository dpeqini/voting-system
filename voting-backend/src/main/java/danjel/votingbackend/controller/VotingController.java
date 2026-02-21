package danjel.votingbackend.controller;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.service.JwtService;
import danjel.votingbackend.service.VotingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vote")
@Tag(name = "Voting", description = "Vote casting and candidate retrieval")
@SecurityRequirement(name = "Bearer Authentication")
public class VotingController {

    private final VotingService votingService;
    private final JwtService    jwtService;

    public VotingController(VotingService votingService, JwtService jwtService) {
        this.votingService = votingService;
        this.jwtService    = jwtService;
    }

    // ── Cast vote ─────────────────────────────────────────────────────────────

    @Operation(
            summary = "Cast a vote",
            description = """
                    Submits a vote for the authenticated voter.
                    Requirements:
                    - Valid JWT (issued after ID card + face verification)
                    - Election must be active
                    - Voter must not have already voted in this election
                    - Candidate must be from voter's region
                      (county for Parliamentary, municipality for Local Government)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote cast successfully",
                    content = @Content(schema = @Schema(implementation = VoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid vote or voter not eligible"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "409", description = "Already voted in this election")
    })
    @PostMapping
    public ResponseEntity<VoteResponse> castVote(
            HttpServletRequest httpRequest,
            @Valid @RequestBody VoteRequest request) {

        String voterId = extractVoterId(httpRequest);
        return ResponseEntity.ok(votingService.castVote(voterId, request));
    }

    // ── Get candidates ────────────────────────────────────────────────────────

    @Operation(
            summary = "Get candidates for voter",
            description = "Returns candidates filtered to the voter's county or municipality"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of available candidates"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Election not found")
    })
    @GetMapping("/candidates/{electionId}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesForVoter(
            HttpServletRequest httpRequest,
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        String voterId = extractVoterId(httpRequest);
        return ResponseEntity.ok(votingService.getCandidatesForVoter(voterId, electionId));
    }

    // ── Vote status ───────────────────────────────────────────────────────────

    @Operation(
            summary = "Check vote status",
            description = "Returns whether the authenticated voter has already voted in the given election"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote status retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/status/{electionId}")
    public ResponseEntity<VoteStatusResponse> getVoteStatus(
            HttpServletRequest httpRequest,
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        String voterId   = extractVoterId(httpRequest);
        boolean hasVoted = votingService.hasVoted(voterId, electionId);

        VoteStatusResponse response = new VoteStatusResponse();
        response.setElectionId(electionId);
        response.setHasVoted(hasVoted);
        response.setVoterId(voterId);
        return ResponseEntity.ok(response);
    }

    // ── JWT helper ────────────────────────────────────────────────────────────

    /**
     * Extracts the voterId claim from the Bearer JWT.
     * Avoids an extra DB round-trip — the voter's DB uuid is embedded in the
     * token by IdCardAuthService at issue time.
     * The filter has already validated the token before we get here.
     */
    private String extractVoterId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtService.extractVoterId(token);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class VoteStatusResponse {
        private String  electionId;
        private String  voterId;
        private boolean hasVoted;
    }
}