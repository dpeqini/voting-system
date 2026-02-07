package danjel.votingbackend.controller;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.VotingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/vote")
@Tag(name = "Voting", description = "Vote casting and candidate retrieval")
@SecurityRequirement(name = "Bearer Authentication")
public class VotingController {

    private final VotingService votingService;
    private final AuthService authService;

    public VotingController(VotingService votingService, AuthService authService) {
        this.votingService = votingService;
        this.authService = authService;
    }

    @Operation(
            summary = "Cast a vote",
            description = """
            Submits a vote for the authenticated voter. Requirements:
            - Voter must be verified
            - Election must be active
            - Voter must not have already voted in this election
            - Candidate must be from voter's region (county for Parliamentary, municipality for Local Government)
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote cast successfully",
                    content = @Content(schema = @Schema(implementation = VoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid vote request or voter not eligible"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "409", description = "Already voted in this election")
    })
    @PostMapping
    public ResponseEntity<VoteResponse> castVote(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody VoteRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        VoteResponse response = votingService.castVote(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get candidates for voter",
            description = "Returns candidates available for the authenticated voter based on their region and the election type"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of available candidates"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Election not found")
    })
    @GetMapping("/candidates/{electionId}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesForVoter(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        List<CandidateResponse> candidates = votingService.getCandidatesForVoter(voter.getId(), electionId)
                .stream()
                .map(this::mapCandidateToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(candidates);
    }

    @Operation(
            summary = "Check vote status",
            description = "Checks if the authenticated voter has already voted in the specified election"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote status retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/status/{electionId}")
    public ResponseEntity<VoteStatusResponse> getVoteStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Election ID") @PathVariable String electionId) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        boolean hasVoted = votingService.hasVoted(voter.getId(), electionId);

        VoteStatusResponse response = new VoteStatusResponse();
        response.setElectionId(electionId);
        response.setHasVoted(hasVoted);
        response.setVoterId(voter.getId());

        return ResponseEntity.ok(response);
    }

    private CandidateResponse mapCandidateToResponse(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        response.setId(candidate.getId());
        response.setFirstName(candidate.getFirstName());
        response.setLastName(candidate.getLastName());
        response.setFullName(candidate.getFullName());
        response.setBiography(candidate.getBiography());
        response.setPhotoUrl(candidate.getPhotoUrl());
        response.setCounty(candidate.getCounty());
        response.setCountyName(candidate.getCounty() != null ? candidate.getCounty().getDisplayName() : null);
        response.setMunicipality(candidate.getMunicipality());
        response.setMunicipalityName(candidate.getMunicipality() != null ? candidate.getMunicipality().getDisplayName() : null);
        response.setPositionInList(candidate.getPositionInList());
        response.setIndependent(candidate.isIndependent());
        response.setProfession(candidate.getProfession());
        response.setAge(candidate.getAge());
        response.setEducation(candidate.getEducation());
        response.setPlatform(candidate.getPlatform());

        if (candidate.getParty() != null) {
            response.setPartyId(candidate.getParty().getId());
            response.setPartyName(candidate.getParty().getName());
            response.setPartyCode(candidate.getParty().getPartyCode());
        }

        return response;
    }

    @Setter
    @Getter
    public static class VoteStatusResponse {
        private String electionId;
        private String voterId;
        private boolean hasVoted;

    }
}
