package danjel.votingbackend.model;

import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a voter authenticated via their Albanian biometric ID card (NFC chip).
 *
 * There is NO password. Identity is established entirely by:
 *   1. NFC chip read (passive authentication against CSCA)
 *   2. ML Kit liveness detection on the Android device
 *   3. DeepFace comparison: chip photo vs live camera frame
 *
 * If the national ID exists in the DB → return existing voter.
 * If not → auto-create from chip data (first-time registration).
 */
@Getter
@Setter
@Entity
@Table(
        name = "voters",
        indexes = {
                @Index(name = "idx_voter_national_id",   columnList = "nationalId",   unique = true),
                @Index(name = "idx_voter_county",         columnList = "county"),
                @Index(name = "idx_voter_municipality",   columnList = "municipality")
        }
)
public class Voter extends BaseEntity {

    // ── Identity (from NFC chip) ───────────────────────────────────────────────

    /** Albanian national ID number (10-char, e.g. "I12345678A"). */
    @Column(nullable = false, unique = true)
    private String nationalId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(columnDefinition = "TEXT")
    private String publicKey;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    /** Expiry date read from chip — validated on every login attempt. */
    @Column(nullable = false)
    private LocalDate cardExpiryDate;

    // ── Region (determines ballot) ────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlbanianCounty county;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlbanianMunicipality municipality;

    // ── Voting state ──────────────────────────────────────────────────────────

    /**
     * Set of election IDs this voter has already cast a ballot for.
     * Maintained alongside the Vote table for fast O(1) duplicate-vote checks.
     */
    @ElementCollection
    @CollectionTable(
            name  = "voter_voted_elections",
            joinColumns = @JoinColumn(name = "voter_id")
    )
    @Column(name = "election_id")
    private Set<String> votedElectionIds = new HashSet<>();

    // ── Administrative ────────────────────────────────────────────────────────

    /**
     * Only set to false manually by an admin in a fraud investigation.
     * Not set by failed face checks — the session simply doesn't issue a token.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Timestamp of the last successful ID-card authentication. */
    @Column
    private LocalDateTime lastAuthenticatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Voter() {}

    public Voter(String nationalId, String firstName, String lastName,
                 LocalDate dateOfBirth, LocalDate cardExpiryDate,
                 AlbanianCounty county, AlbanianMunicipality municipality, String publicKey) {
        this.nationalId     = nationalId;
        this.firstName      = firstName;
        this.lastName       = lastName;
        this.dateOfBirth    = dateOfBirth;
        this.cardExpiryDate = cardExpiryDate;
        this.county         = county;
        this.municipality   = municipality;
        this.publicKey     = publicKey;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean hasVotedIn(String electionId) {
        return votedElectionIds.contains(electionId);
    }

    public void recordVote(String electionId) {
        votedElectionIds.add(electionId);
    }

    /** Age check: must be ≥ 18 at the time of this call. */
    public boolean isAgeEligible() {
        return !LocalDate.now().minusYears(18).isBefore(dateOfBirth);
    }

    /** Card must not be expired. */
    public boolean isCardValid() {
        return !cardExpiryDate.isBefore(LocalDate.now());
    }
}