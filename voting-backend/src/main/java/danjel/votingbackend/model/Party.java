package danjel.votingbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(
        name = "election_party",
        indexes = {
                @Index(name = "idx_party_external_id", columnList = "externalId")
        },
        // partyCode is unique PER election, not globally
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_party_code_election",
                        columnNames = {"partyCode", "election_id"})
        }
)
public class Party extends BaseEntity {

    /**
     * Short political party code (e.g. "PS", "PD").
     * Unique within an election; the same code can reappear in future elections.
     */
    @Column(nullable = false)
    private String partyCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column
    private String logoUrl;

    @Column
    private String color;

    @Column
    private String leader;

    @Column
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Candidate> candidates = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private int listNumber;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Party() {}

    public Party(String partyCode, String name) {
        this.partyCode = partyCode;
        this.name      = name;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addCandidate(Candidate candidate) {
        candidates.add(candidate);
        candidate.setParty(this);
    }

    public void removeCandidate(Candidate candidate) {
        candidates.remove(candidate);
        candidate.setParty(null);
    }
}