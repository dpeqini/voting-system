package danjel.votingbackend.controller;

import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.dto.election.ElectionRequest;
import danjel.votingbackend.dto.election.ElectionResponse;
import danjel.votingbackend.dto.election.PartyResponse;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.service.AuthService;
import danjel.votingbackend.service.ElectionService;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import danjel.votingbackend.utils.enums.ElectionStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/elections")
public class ElectionController {

    private final ElectionService electionService;
    private final AuthService authService;

    public ElectionController(ElectionService electionService, AuthService authService) {
        this.electionService = electionService;
        this.authService = authService;
    }

    // ==================== Admin Endpoints ====================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> createElection(@Valid @RequestBody ElectionRequest request) {
        ElectionResponse response = electionService.createElection(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{electionId}/import-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> importCandidates(
            @PathVariable String electionId,
            @RequestParam(required = false) String dataSourceUrl) {
        ElectionResponse response = electionService.importCandidates(electionId, dataSourceUrl);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{electionId}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> startElection(@PathVariable String electionId) {
        ElectionResponse response = electionService.startElection(electionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{electionId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> closeElection(
            @PathVariable String electionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ElectionResponse response = electionService.closeElection(electionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // ==================== Public Endpoints ====================

    @GetMapping("/active")
    public ResponseEntity<List<ElectionResponse>> getActiveElections() {
        List<ElectionResponse> elections = electionService.getActiveElections();
        return ResponseEntity.ok(elections);
    }

    @GetMapping("/{electionId}/public")
    public ResponseEntity<ElectionResponse> getElectionPublic(@PathVariable String electionId) {
        ElectionResponse response = electionService.getElection(electionId);
        return ResponseEntity.ok(response);
    }

    // ==================== Authenticated Endpoints ====================

    @GetMapping
    public ResponseEntity<Page<ElectionResponse>> getAllElections(Pageable pageable) {
        Page<ElectionResponse> elections = electionService.getAllElections(pageable);
        return ResponseEntity.ok(elections);
    }

    @GetMapping("/{electionId}")
    public ResponseEntity<ElectionResponse> getElection(@PathVariable String electionId) {
        ElectionResponse response = electionService.getElection(electionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ElectionResponse>> getElectionsByStatus(@PathVariable ElectionStatus status) {
        List<ElectionResponse> elections = electionService.getElectionsByStatus(status);
        return ResponseEntity.ok(elections);
    }

    @GetMapping("/{electionId}/candidates")
    public ResponseEntity<List<CandidateResponse>> getCandidates(@PathVariable String electionId) {
        List<CandidateResponse> candidates = electionService.getCandidatesForElection(electionId);
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/{electionId}/candidates/my-region")
    public ResponseEntity<List<CandidateResponse>> getCandidatesForMyRegion(
            @PathVariable String electionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId,
                voter.getCounty(),
                voter.getMunicipality()
        );
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/{electionId}/candidates/county/{county}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesByCounty(
            @PathVariable String electionId,
            @PathVariable AlbanianCounty county) {
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId, county, null
        );
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/{electionId}/candidates/municipality/{municipality}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesByMunicipality(
            @PathVariable String electionId,
            @PathVariable AlbanianMunicipality municipality) {
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId, null, municipality
        );
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/{electionId}/parties")
    public ResponseEntity<List<PartyResponse>> getParties(@PathVariable String electionId) {
        List<PartyResponse> parties = electionService.getPartiesForElection(electionId);
        return ResponseEntity.ok(parties);
    }
}
