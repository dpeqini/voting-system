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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Elections", description = "Election creation and management")
public class ElectionController {

    private final ElectionService electionService;
    private final AuthService authService;

    public ElectionController(ElectionService electionService, AuthService authService) {
        this.electionService = electionService;
        this.authService = authService;
    }

    // ==================== Admin Endpoints ====================

    @Operation(
            summary = "Create a new election",
            description = "Creates a new election. Only accessible by ADMIN users.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Election created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> createElection(@Valid @RequestBody ElectionRequest request) {
        ElectionResponse response = electionService.createElection(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Import candidates for an election",
            description = "Imports candidates and parties from external data source. Admin only.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidates imported successfully"),
            @ApiResponse(responseCode = "400", description = "Election not in correct state for import"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only"),
            @ApiResponse(responseCode = "404", description = "Election not found")
    })
    @PostMapping("/{electionId}/import-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> importCandidates(
            @Parameter(description = "Election ID") @PathVariable String electionId,
            @Parameter(description = "Optional external data source URL") @RequestParam(required = false) String dataSourceUrl) {
        ElectionResponse response = electionService.importCandidates(electionId, dataSourceUrl);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Start an election",
            description = "Starts the election and initializes the blockchain. Admin only.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Election started successfully"),
            @ApiResponse(responseCode = "400", description = "Election not ready to start (candidates not imported)"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only")
    })
    @PostMapping("/{electionId}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> startElection(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        ElectionResponse response = electionService.startElection(electionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Close an election",
            description = "Closes the election and finalizes all pending votes. Admin only.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Election closed successfully"),
            @ApiResponse(responseCode = "400", description = "Election is not currently active"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin only")
    })
    @PostMapping("/{electionId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponse> closeElection(
            @Parameter(description = "Election ID") @PathVariable String electionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        ElectionResponse response = electionService.closeElection(electionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // ==================== Public Endpoints ====================

    @Operation(
            summary = "Get active elections",
            description = "Returns all currently active elections. No authentication required."
    )
    @ApiResponse(responseCode = "200", description = "List of active elections")
    @GetMapping("/active")
    public ResponseEntity<List<ElectionResponse>> getActiveElections() {
        List<ElectionResponse> elections = electionService.getActiveElections();
        return ResponseEntity.ok(elections);
    }

    @Operation(
            summary = "Get election public info",
            description = "Returns public information about an election. No authentication required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Election details"),
            @ApiResponse(responseCode = "404", description = "Election not found")
    })
    @GetMapping("/{electionId}/public")
    public ResponseEntity<ElectionResponse> getElectionPublic(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        ElectionResponse response = electionService.getElection(electionId);
        return ResponseEntity.ok(response);
    }

    // ==================== Authenticated Endpoints ====================

    @Operation(
            summary = "Get all elections",
            description = "Returns paginated list of all elections",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    public ResponseEntity<Page<ElectionResponse>> getAllElections(Pageable pageable) {
        Page<ElectionResponse> elections = electionService.getAllElections(pageable);
        return ResponseEntity.ok(elections);
    }

    @Operation(
            summary = "Get election by ID",
            description = "Returns detailed information about a specific election",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}")
    public ResponseEntity<ElectionResponse> getElection(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        ElectionResponse response = electionService.getElection(electionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get elections by status",
            description = "Returns all elections with the specified status",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ElectionResponse>> getElectionsByStatus(
            @Parameter(description = "Election status", schema = @Schema(implementation = ElectionStatus.class))
            @PathVariable ElectionStatus status) {
        List<ElectionResponse> elections = electionService.getElectionsByStatus(status);
        return ResponseEntity.ok(elections);
    }

    @Operation(
            summary = "Get all candidates for election",
            description = "Returns all candidates for the specified election",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}/candidates")
    public ResponseEntity<List<CandidateResponse>> getCandidates(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        List<CandidateResponse> candidates = electionService.getCandidatesForElection(electionId);
        return ResponseEntity.ok(candidates);
    }

    @Operation(
            summary = "Get candidates for voter's region",
            description = "Returns candidates based on the authenticated voter's county/municipality",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}/candidates/my-region")
    public ResponseEntity<List<CandidateResponse>> getCandidatesForMyRegion(
            @Parameter(description = "Election ID") @PathVariable String electionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Voter voter = authService.getCurrentVoter(userDetails.getUsername());
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId,
                voter.getCounty(),
                voter.getMunicipality()
        );
        return ResponseEntity.ok(candidates);
    }

    @Operation(
            summary = "Get candidates by county",
            description = "Returns candidates for a specific county (Parliamentary elections)",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}/candidates/county/{county}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesByCounty(
            @Parameter(description = "Election ID") @PathVariable String electionId,
            @Parameter(description = "Albanian county") @PathVariable AlbanianCounty county) {
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId, county, null
        );
        return ResponseEntity.ok(candidates);
    }

    @Operation(
            summary = "Get candidates by municipality",
            description = "Returns candidates for a specific municipality (Local Government elections)",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}/candidates/municipality/{municipality}")
    public ResponseEntity<List<CandidateResponse>> getCandidatesByMunicipality(
            @Parameter(description = "Election ID") @PathVariable String electionId,
            @Parameter(description = "Albanian municipality") @PathVariable AlbanianMunicipality municipality) {
        List<CandidateResponse> candidates = electionService.getCandidatesForVoterRegion(
                electionId, null, municipality
        );
        return ResponseEntity.ok(candidates);
    }

    @Operation(
            summary = "Get parties for election",
            description = "Returns all political parties participating in the election",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{electionId}/parties")
    public ResponseEntity<List<PartyResponse>> getParties(
            @Parameter(description = "Election ID") @PathVariable String electionId) {
        List<PartyResponse> parties = electionService.getPartiesForElection(electionId);
        return ResponseEntity.ok(parties);
    }
}
