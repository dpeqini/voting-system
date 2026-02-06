package danjel.votingbackend.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "blocks", indexes = {
        @Index(name = "idx_block_hash", columnList = "blockHash", unique = true),
        @Index(name = "idx_block_number", columnList = "blockNumber"),
        @Index(name = "idx_block_election", columnList = "election_id"),
        @Index(name = "idx_block_previous_hash", columnList = "previousHash")
})
public class Block extends BaseEntity {

    // Getters and Setters
    @Column(nullable = false)
    private Long blockNumber;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, unique = true)
    private String blockHash;

    @Column(nullable = false)
    private String previousHash;

    @Column(nullable = false)
    private String merkleRoot;

    @Column(nullable = false)
    private Long nonce;

    @Column(nullable = false)
    private int difficulty;

    @ElementCollection
    @CollectionTable(name = "block_vote_hashes", joinColumns = @JoinColumn(name = "block_id"))
    @Column(name = "vote_hash")
    private List<String> voteHashes = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false)
    private boolean validated = false;

    @Column
    private LocalDateTime validatedAt;

    @Column
    private String validatorSignature;

    @Column(nullable = false)
    private int transactionCount = 0;

    // Constructors
    public Block() {}

    public Block(Long blockNumber, String previousHash, Election election) {
        this.blockNumber = blockNumber;
        this.previousHash = previousHash;
        this.election = election;
        this.timestamp = LocalDateTime.now();
        this.nonce = 0L;
        this.difficulty = 4;
    }

    public void addVoteHash(String voteHash) {
        this.voteHashes.add(voteHash);
        this.transactionCount++;
    }
}
