package danjel.votingbackend.controller;

import danjel.votingbackend.dto.ElectionResultsDto;
import danjel.votingbackend.service.ResultsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes election results over HTTP.
 *
 * GET /api/v1/results/election/{electionId}
 * GET /api/v1/results/election/{electionId}?region=TIRANE
 *
 * This endpoint is intentionally PUBLIC — no JWT is required.  Election
 * results are a transparency feature that any citizen, journalist, or
 * international observer should be able to query without an account.
 * The response contains only candidate names, party names, and vote counts —
 * no voter identity, no private data.
 *
 * You must permit this path in your SecurityConfig:
 *   .requestMatchers("/api/v1/results/**").permitAll()
 *
 * Error responses:
 *   404  — electionId does not exist in the database.
 *   400  — the region query parameter is present but does not match any
 *           known AlbanianCounty or AlbanianMunicipality enum constant.
 */
@RestController
@RequestMapping("/api/v1/results")
@CrossOrigin(origins = "*") // lock this down to your frontend domain before going to production
public class ResultsController {

    private final ResultsService resultsService;

    public ResultsController(ResultsService resultsService) {
        this.resultsService = resultsService;
    }

    @GetMapping("/election/{electionId}")
    public ResponseEntity<?> getResults(
            @PathVariable String electionId,
            @RequestParam(required = false) String region) {

        try {
            ElectionResultsDto results = resultsService.getResults(electionId, region);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            // ResultsService throws IllegalArgumentException for two reasons:
            // election not found (→ 404) and unrecognised region string (→ 400).
            // We distinguish them by checking the message prefix.
            if (ex.getMessage().startsWith("Election not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
