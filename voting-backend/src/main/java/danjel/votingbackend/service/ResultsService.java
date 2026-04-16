package danjel.votingbackend.service;

import danjel.votingbackend.dto.CandidateResultDto;
import danjel.votingbackend.dto.ElectionResultsDto;
import danjel.votingbackend.dto.PartyResultDto;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.VoteRepository;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ResultsService is a pure read-service: every method opens a read-only
 * transaction, executes aggregate JPQL queries, enriches the raw counts with
 * percentage values, and returns an immutable DTO.
 *
 * There is intentionally no caching in this class.  Because we want the UI
 * to always reflect the current state of the Vote table, every HTTP request
 * from Angular hits the database.  The queries are GROUP BY COUNT(*) aggregates
 * on indexed foreign-key columns, so they are fast even with tens of thousands
 * of rows.  If latency ever becomes a problem, a @Cacheable with a 10-second
 * TTL can be added as a one-line change without touching any other code.
 *
 * The `region` parameter is interpreted differently depending on election type:
 *   - PARLIAMENTARY elections  → region is a county name  (or null = all counties)
 *   - LOCAL_GOVERNMENT elections → region is a municipality name (or null = all)
 * ResultsService does not know or care which interpretation is correct; it
 * simply delegates to the correct VoteRepository method based on which
 * parameter is provided.  The controller is responsible for documenting which
 * elections accept which kind of region string.
 */
@Service
@Transactional(readOnly = true)
public class ResultsService {

    private final VoteRepository     voteRepository;
    private final ElectionRepository electionRepository;

    public ResultsService(VoteRepository voteRepository,
                          ElectionRepository electionRepository) {
        this.voteRepository     = voteRepository;
        this.electionRepository = electionRepository;
    }

    /**
     * Computes and returns the full results snapshot for the given election,
     * optionally filtered to a single region (county or municipality).
     *
     * Throws IllegalArgumentException if the election ID does not exist —
     * the controller converts this to a 404 response.
     */
    public ElectionResultsDto getResults(String electionId, String region) {

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Election not found: " + electionId));

        boolean isParliamentary =
                "PARLIAMENTARY".equalsIgnoreCase(election.getElectionType().name());

        // ── 1. Fetch raw counts from the DB ───────────────────────────────
        // Three separate queries (candidate, party, total) each run in
        // roughly O(n_votes) time with a single table scan + hash aggregate.
        List<CandidateResultDto> candidates;
        List<PartyResultDto>     parties;
        long                     total;

        boolean noRegionFilter = (region == null || region.isBlank());

        if (noRegionFilter) {
            candidates = voteRepository.findCandidateResults(electionId);
            parties    = voteRepository.findPartyResults(electionId);
            total      = voteRepository.countVotesByElection(electionId);

        } else if (isParliamentary) {
            // For parliamentary elections the region string is a county name
            candidates = voteRepository.findCandidateResultsByCounty(electionId, AlbanianCounty.valueOf(region));
            parties    = voteRepository.findPartyResultsByCounty(electionId, AlbanianCounty.valueOf(region));
            total      = voteRepository.countVotesByElectionAndCounty(electionId, AlbanianCounty.valueOf(region));

        } else {
            // For local-government elections the region string is a municipality name
            candidates = voteRepository.findCandidateResultsByMunicipality(electionId, AlbanianMunicipality.valueOf(region));
            parties    = voteRepository.findPartyResultsByMunicipality(electionId, AlbanianMunicipality.valueOf(region));
            total      = voteRepository.countVotesByElectionAndMunicipality(electionId, AlbanianMunicipality.valueOf(region));
        }

        // ── 2. Enrich with percentage values ──────────────────────────────
        // We guard against division-by-zero: if the election just started and
        // no votes have been cast yet, every percentage stays at 0.0.
        if (total > 0) {
            candidates.forEach(c ->
                    c.setPercentage((c.getVoteCount() * 100.0) / total));
            parties.forEach(p ->
                    p.setPercentage((p.getTotalVotes() * 100.0) / total));
        }

        // ── 3. Assemble and return ────────────────────────────────────────
        return ElectionResultsDto.builder()
                .electionId(election.getId())
                .electionName(election.getName())
                .electionType(election.getElectionType().name())
                .electionStatus(election.getStatus().name())
                .totalVotes(total)
                .computedAt(LocalDateTime.now())
                .regionFilter(noRegionFilter ? null : region)
                .byCandidate(candidates)
                .byParty(parties)
                .build();
    }
}
