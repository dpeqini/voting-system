package danjel.votingbackend.model;


import danjel.votingbackend.utils.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "voters", indexes = {
        @Index(name = "idx_voter_national_id", columnList = "nationalId", unique = true),
        @Index(name = "idx_voter_email", columnList = "email", unique = true),
        @Index(name = "idx_voter_county", columnList = "county"),
        @Index(name = "idx_voter_municipality", columnList = "municipality")
})
public class Voter extends BaseEntity {

    // Getters and Setters
    @Column(nullable = false, unique = true)
    private String nationalId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlbanianCounty county;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlbanianMunicipality municipality;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.VOTER;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private boolean faceVerified = false;

    @Column(columnDefinition = "BYTEA")
    private byte[] faceEncodingData;

    @Column
    private String publicKey;

    @ElementCollection
    @CollectionTable(name = "voter_voted_elections", joinColumns = @JoinColumn(name = "voter_id"))
    @Column(name = "election_id")
    private Set<String> votedElectionIds = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean accountLocked = false;

    @Column
    private int failedLoginAttempts = 0;

    // Constructors
    public Voter() {}

    public Voter(String nationalId, String firstName, String lastName, String email,
                 String passwordHash, LocalDate dateOfBirth, AlbanianCounty county,
                 AlbanianMunicipality municipality, String address) {
        this.nationalId = nationalId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.dateOfBirth = dateOfBirth;
        this.county = county;
        this.municipality = municipality;
        this.address = address;
    }

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean hasVotedIn(String electionId) {
        return votedElectionIds.contains(electionId);
    }

    public void markAsVoted(String electionId) {
        votedElectionIds.add(electionId);
    }

    public boolean isEligibleToVote() {
        return verified && enabled && !accountLocked &&
                LocalDate.now().minusYears(18).isAfter(dateOfBirth) ||
                LocalDate.now().minusYears(18).isEqual(dateOfBirth);
    }

}