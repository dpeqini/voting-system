package danjel.votingbackend.model;

import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "votes", indexes = {
        @Index(name = "idx_vote_election",       columnList = "election_id"),
        @Index(name = "idx_vote_candidate",      columnList = "candidate_id"),
        @Index(name = "idx_vote_party",          columnList = "party_id"),
        @Index(name = "idx_vote_voter_hash",     columnList = "voterHash"),
        @Index(name = "idx_vote_blockchain_tx",  columnList = "blockchainTransactionId"),
        @Index(name = "idx_vote_timestamp",      columnList = "timestamp"),
        @Index(name = "idx_vote_county",         columnList = "county"),
        @Index(name = "idx_vote_municipality",   columnList = "municipality")
})
public class Vote extends BaseEntity {

    // ── Mandatory relations ───────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /**
     * FK to Candidate.  Optional (null for party-list-only votes in some systems),
     * but always set when the voter picks a specific candidate.
     * Candidate identity is NOT sensitive — only voterHash is anonymous.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    /**
     * FK to Party.  Set together with candidate (or alone for pure party votes).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    // ── Anonymity / security ──────────────────────────────────────────────────

    /**
     * SHA-256(voterId + electionId + salt) — links the ballot to a voter
     * without exposing the voter's identity.
     */
    @Column(nullable = false)
    private String voterHash;

    @Column(nullable = false)
    private String encryptedVoteData;

    @Column(nullable = false, unique = true)
    private String voteHash;

    // ── Blockchain anchoring ──────────────────────────────────────────────────

    @Column
    private String blockchainTransactionId;

    @Column
    private Long blockNumber;

    @Column
    private String previousBlockHash;

    @Column
    private String currentBlockHash;

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private boolean verified = false;

    @Column
    private LocalDateTime verifiedAt;

    @Column
    private String verificationProof;

    @Enumerated(EnumType.STRING)
    @Column
    private AlbanianCounty county;

    @Enumerated(EnumType.STRING)
    @Column
    private AlbanianMunicipality municipality;

    @Column
    private String votingStationId;

    @Column
    private String encryptedMetadata;

    @Column
    private String digitalSignature;

    @Column
    private String nonce;

    /**
     * Receipt token generated at vote-cast time and shown to the voter on-screen.
     * Persisted so GET /verification/vote/receipt/{token} can resolve it later.
     * Indexed for fast lookup.
     */
    @Column(unique = true)
    @org.hibernate.annotations.Index(name = "idx_vote_receipt_token")
    private String receiptToken;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Vote() {}

    public Vote(Election election, String voterHash,
                String encryptedVoteData, String voteHash) {
        this.election         = election;
        this.voterHash        = voterHash;
        this.encryptedVoteData = encryptedVoteData;
        this.voteHash         = voteHash;
        this.timestamp        = LocalDateTime.now();
    }
}