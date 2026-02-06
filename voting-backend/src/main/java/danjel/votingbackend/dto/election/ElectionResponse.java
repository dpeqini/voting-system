package danjel.votingbackend.dto.election;
import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class ElectionResponse {

    // Getters and Setters
    private String id;
    private String name;
    private String description;
    private ElectionType electionType;
    private ElectionStatus status;
    private LocalDateTime electionDate;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime registrationDeadline;
    private long totalEligibleVoters;
    private long totalVotesCast;
    private double turnoutPercentage;
    private boolean candidatesImported;
    private int candidateCount;
    private int partyCount;
    private String blockchainContractAddress;
    private LocalDateTime createdAt;
    private LocalDateTime lastSyncedAt;
    private List<CandidateResponse> candidates;
    private List<PartyResponse> parties;

    // Constructors
    public ElectionResponse() {}

}
