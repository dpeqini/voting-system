package danjel.votingbackend.model;

import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "candidates", indexes = {
        @Index(name = "idx_candidate_election", columnList = "election_id"),
        @Index(name = "idx_candidate_party", columnList = "party_id"),
        @Index(name = "idx_candidate_county", columnList = "county"),
        @Index(name = "idx_candidate_municipality", columnList = "municipality"),
        @Index(name = "idx_candidate_external_id", columnList = "externalId")
})
public class Candidate extends BaseEntity {

    // Getters and Setters
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(length = 2000)
    private String biography;

    @Column
    private String photoUrl;

    @Column
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Enumerated(EnumType.STRING)
    @Column
    private AlbanianCounty county;

    @Enumerated(EnumType.STRING)
    @Column
    private AlbanianMunicipality municipality;

    @Column
    private Integer positionInList;

    @Column(nullable = false)
    private boolean independent = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private String profession;

    @Column
    private Integer age;

    @Column
    private String education;

    @Column(length = 1000)
    private String platform;

    // Constructors
    public Candidate() {}

    public Candidate(String firstName, String lastName, Election election) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.election = election;
    }

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getDisplayName() {
        if (party != null) {
            return getFullName() + " (" + party.getName() + ")";
        }
        return getFullName() + " (Independent)";
    }

}
