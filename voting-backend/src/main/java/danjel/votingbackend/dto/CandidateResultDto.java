package danjel.votingbackend.dto;

/**
 * Projection DTO for a single candidate's vote tally in an election.
 *
 * The `percentage` field is intentionally NOT part of the constructor because
 * it cannot be computed inside a single GROUP BY query without knowing the
 * total first. ResultsService fills it in after summing all vote counts.
 */
public class CandidateResultDto {

    private final String candidateId;
    private final String candidateName;
    private final String partyName;
    private final String partyAbbreviation;

    // For PARLIAMENTARY elections: county name; for LOCAL_GOVERNMENT: municipality name.
    private final String region;

    private final long voteCount;

    // Mutable — set by ResultsService once the grand total is known.
    private double percentage;

    // ── Constructor used by JPQL ───────────────────────────────────────────
    public CandidateResultDto(String candidateId,
                              String candidateName,
                              String partyName,
                              String partyAbbreviation,
                              String region,
                              Long voteCount) {
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.partyName = partyName;
        this.partyAbbreviation = partyAbbreviation;
        this.region = region;
        this.voteCount = voteCount != null ? voteCount : 0L;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public String getCandidateId() { return candidateId; }
    public String getCandidateName() { return candidateName; }
    public String getPartyName() { return partyName; }
    public String getPartyAbbreviation() { return partyAbbreviation; }
    public String getRegion() { return region; }
    public long getVoteCount() { return voteCount; }
    public double getPercentage() { return percentage; }

    // Rounds to 2 decimal places
    public void setPercentage(double raw) {
        this.percentage = Math.round(raw * 100.0) / 100.0;
    }
}