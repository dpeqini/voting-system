package danjel.votingbackend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Root response object returned by GET /api/v1/results/election/{id}.
 *
 * Wrapping both lists (byCandidate and byParty) in a single object means the
 * Angular component makes exactly ONE HTTP call and can render both the
 * candidate tab and the party tab from that one response.
 *
 * Built with a static builder so ResultsService reads cleanly without
 * long constructor argument lists.
 */
public class ElectionResultsDto {

    private final String electionId;
    private final String electionName;
    private final String electionType;    // "PARLIAMENTARY" | "LOCAL_GOVERNMENT"
    private final String electionStatus;  // "STARTED" | "CLOSED" | …
    private final long totalVotes;
    private final LocalDateTime computedAt;
    private final String regionFilter;    // null = no filter applied
    private final List<CandidateResultDto> byCandidate;
    private final List<PartyResultDto> byParty;

    private ElectionResultsDto(Builder b) {
        this.electionId     = b.electionId;
        this.electionName   = b.electionName;
        this.electionType   = b.electionType;
        this.electionStatus = b.electionStatus;
        this.totalVotes     = b.totalVotes;
        this.computedAt     = b.computedAt;
        this.regionFilter   = b.regionFilter;
        this.byCandidate    = b.byCandidate;
        this.byParty        = b.byParty;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public String getElectionId() { return electionId; }
    public String getElectionName() { return electionName; }
    public String getElectionType() { return electionType; }
    public String getElectionStatus() { return electionStatus; }
    public long getTotalVotes() { return totalVotes; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public String getRegionFilter() { return regionFilter; }
    public List<CandidateResultDto> getByCandidate() { return byCandidate; }
    public List<PartyResultDto> getByParty() { return byParty; }

    // ── Builder ───────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String electionId;
        private String electionName;
        private String electionType;
        private String electionStatus;
        private long totalVotes;
        private LocalDateTime computedAt = LocalDateTime.now();
        private String regionFilter;
        private List<CandidateResultDto> byCandidate;
        private List<PartyResultDto> byParty;

        public Builder electionId(String v) { electionId = v; return this; }
        public Builder electionName(String v) { electionName = v; return this; }
        public Builder electionType(String v) { electionType = v; return this; }
        public Builder electionStatus(String v) { electionStatus = v; return this; }
        public Builder totalVotes(long v) { totalVotes = v; return this; }
        public Builder computedAt(LocalDateTime v) { computedAt = v; return this; }
        public Builder regionFilter(String v) { regionFilter = v; return this; }
        public Builder byCandidate(List<CandidateResultDto> v) { byCandidate = v; return this; }
        public Builder byParty(List<PartyResultDto> v) { byParty = v; return this; }
        public ElectionResultsDto build() { return new ElectionResultsDto(this); }
    }
}