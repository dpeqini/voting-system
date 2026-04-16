package danjel.votingbackend.dto;

/**
 * Projection DTO for a single party's aggregated vote tally in an election.
 */
public class PartyResultDto {

    private final String partyId;
    private final String partyName;
    private final String partyCode;
    private final long totalVotes;

    // Set by ResultsService after the total is summed.
    private double percentage;

    // ── Constructor used by JPQL ───────────────────────────────────────────
    public PartyResultDto(String partyId,
                          String partyName,
                          String partyCode,
                          Long totalVotes) {
        this.partyId = partyId;
        this.partyName = partyName;
        this.partyCode = partyCode;
        this.totalVotes = totalVotes != null ? totalVotes : 0L;
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public String getPartyId() { return partyId; }
    public String getPartyName() { return partyName; }
    public String getPartyCode() { return partyCode; }
    public long getTotalVotes() { return totalVotes; }
    public double getPercentage() { return percentage; }

    public void setPercentage(double raw) {
        this.percentage = Math.round(raw * 100.0) / 100.0;
    }
}