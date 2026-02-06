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
        @Index(name = "idx_vote_election", columnList = "election_id"),
        @Index(name = "idx_vote_voter_hash", columnList = "voterHash"),
        @Index(name = "idx_vote_blockchain_tx", columnList = "blockchainTransactionId"),
        @Index(name = "idx_vote_timestamp", columnList = "timestamp"),
        @Index(name = "idx_vote_county", columnList = "county"),
        @Index(name = "idx_vote_municipality", columnList = "municipality")
})
public class Vote extends BaseEntity {

    // Getters and Setters
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false)
    private String voterHash;

    @Column(nullable = false)
    private String encryptedVoteData;

    @Column(nullable = false)
    private String voteHash;

    @Column
    private String candidateId;

    @Column
    private String partyId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String blockchainTransactionId;

    @Column
    private Long blockNumber;

    @Column
    private String previousBlockHash;

    @Column
    private String currentBlockHash;

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

    // Constructors
    public Vote() {}

    public Vote(Election election, String voterHash, String encryptedVoteData, String voteHash) {
        this.election = election;
        this.voterHash = voterHash;
        this.encryptedVoteData = encryptedVoteData;
        this.voteHash = voteHash;
        this.timestamp = LocalDateTime.now();
    }

}