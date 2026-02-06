package danjel.votingbackend.controller;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.VotingService;
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
public class VotingController {

    private final VotingService votingService;
    private final AuthService authService;

    public VotingController(VotingService votingService, AuthService authService) {
        this.votingService = votingService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<VoteResponse> castVote(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody VoteRequest request) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        VoteResponse response = votingService.castVote(voter.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/candidates/{electionId}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesForVoter(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String electionId) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        List<CandidateResponse> candidates = votingService.getCandidatesForVoter(voter.getId(), electionId)
                .stream()
                .map(this::mapCandidateToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/status/{electionId}")
    public ResponseEntity<VoteStatusResponse> getVoteStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String electionId) {

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