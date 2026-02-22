package danjel.votingbackend.model;

import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "elections", indexes = {
        @Index(name = "idx_election_status", columnList = "status"),
        @Index(name = "idx_election_type", columnList = "electionType"),
        @Index(name = "idx_election_start_date", columnList = "startDate"),
        @Index(name = "idx_election_end_date", columnList = "endDate")
})
public class Election extends BaseEntity {

    // Getters and Setters
    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ElectionType electionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ElectionStatus status = ElectionStatus.CREATED;

    @Column(nullable = false)
    private LocalDateTime electionDate;

    @Column
    private LocalDateTime startDate;

    @Column
    private LocalDateTime endDate;

    @Column(nullable = false)
    private LocalDateTime registrationDeadline;

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Candidate> candidates = new HashSet<>();

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Party> parties = new HashSet<>();

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Vote> votes = new HashSet<>();

    @Column
    private String blockchainContractAddress;

    @Column
    private String genesisBlockHash;

    @Column(nullable = false)
    private long totalEligibleVoters = 0;

    @Column(nullable = false)
    private long totalVotesCast = 0;

    @Column
    private String resultsPublishedBy;

    @Column
    private LocalDateTime resultsPublishedAt;

    @Column(nullable = false)
    private boolean candidatesImported = false;

    @Column
    private String externalDataSource;

    @Column
    private LocalDateTime lastSyncedAt;

    // Constructors
    public Election() {}

    public Election(String name, ElectionType electionType, LocalDateTime electionDate,
                    LocalDateTime registrationDeadline) {
        this.name = name;
        this.electionType = electionType;
        this.electionDate = electionDate;
        this.registrationDeadline = registrationDeadline;
    }

    // Helper methods
    public boolean isActive() {
        return status == ElectionStatus.STARTED;
    }

    public boolean canVote() {
        LocalDateTime now = LocalDateTime.now();
        return isActive() &&
                (startDate == null || now.isAfter(startDate)) &&
                (endDate == null || now.isBefore(endDate));
    }

    public boolean canImportCandidates() {
        return status == ElectionStatus.CREATED || status == ElectionStatus.CANDIDATES_IMPORTED;
    }

    public void incrementVoteCount() {
        this.totalVotesCast++;
    }

    public double getTurnoutPercentage() {
        if (totalEligibleVoters == 0) return 0.0;
        return (double) totalVotesCast / totalEligibleVoters * 100;
    }

    public void addCandidate(Candidate candidate) {
        candidates.add(candidate);
        candidate.setElection(this);
    }

    public void addParty(Party party) {
        parties.add(party);
        party.setElection(this);
    }
}
