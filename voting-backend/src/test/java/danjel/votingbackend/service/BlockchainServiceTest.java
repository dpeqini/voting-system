package danjel.votingbackend.service;

import danjel.votingbackend.config.BlockchainConfig;
import danjel.votingbackend.dto.VerificationResponse;
import danjel.votingbackend.model.Block;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Vote;
import danjel.votingbackend.repository.BlockRepository;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.VoteRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the private-blockchain core (BlockchainService).
 *
 * These are pure unit tests — no database, no Spring context. Repositories are
 * mocked; a real RSA KeyPair and a real BlockchainConfig are used so that mining
 * (Proof-of-Work), block hashing, Merkle-root computation, RSA signing (Proof-of-
 * Authority) and chain validation all run against the genuine cryptographic code.
 *
 * The chain-validation rules exercised mirror Kreu VI of the thesis:
 *   1. chain link  (previousHash == prior block hash)
 *   2. hash integrity (recomputed SHA-256 == stored hash)
 *   3. RSA signature (validatorSignature verifies against the server public key)
 */
class BlockchainServiceTest {

    private static KeyPair keyPair;

    private BlockRepository    blockRepository;
    private VoteRepository     voteRepository;
    private ElectionRepository electionRepository;
    private BlockchainConfig   config;
    private BlockchainService  service;

    private Election election;
    private List<Block> savedBlocks;
    private List<Vote>  votes;

    private static final String ELECTION_ID = "election-1";

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        blockRepository    = mock(BlockRepository.class);
        voteRepository     = mock(VoteRepository.class);
        electionRepository = mock(ElectionRepository.class);

        config = new BlockchainConfig();
        // Low difficulty keeps PoW mining instant in tests while still exercising it.
        config.setDifficulty(2);

        service = new BlockchainService(blockRepository, voteRepository,
                electionRepository, config, keyPair);

        election = new Election();
        election.setId(ELECTION_ID);
        election.setName("Zgjedhjet Test 2025");

        savedBlocks = new ArrayList<>();
        votes = new ArrayList<>();

        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> {
            savedBlocks.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(electionRepository.save(any(Election.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(electionRepository.findById(ELECTION_ID)).thenReturn(Optional.of(election));
        when(blockRepository.findLatestBlock(ELECTION_ID)).thenAnswer(inv ->
                savedBlocks.isEmpty()
                        ? Optional.empty()
                        : Optional.of(savedBlocks.get(savedBlocks.size() - 1)));
    }

    /** Builds a valid two-block chain: genesis + one mined block of three votes. */
    private void buildValidChain() {
        service.initializeBlockchain(election);          // block 0 (genesis)
        for (int i = 0; i < 3; i++) {
            Vote v = new Vote(election, "voter-" + i, "encrypted-" + i, "voteHash-" + i);
            votes.add(v);
            service.addVoteToBlockchain(v);
        }
        service.processBlock(ELECTION_ID);               // block 1
        when(blockRepository.findByElectionIdOrderByBlockNumberAsc(ELECTION_ID))
                .thenReturn(savedBlocks);
    }

    @Test
    @DisplayName("initializeBlockchain mines a genesis block satisfying the PoW difficulty")
    void genesisBlockSatisfiesProofOfWork() {
        String genesisHash = service.initializeBlockchain(election);

        assertThat(genesisHash).startsWith("00");                 // difficulty = 2
        assertThat(election.getGenesisBlockHash()).isEqualTo(genesisHash);
        assertThat(savedBlocks).hasSize(1);
        assertThat(savedBlocks.get(0).getBlockNumber()).isZero();
        assertThat(savedBlocks.get(0).getValidatorSignature()).isNotBlank();
    }

    @Test
    @DisplayName("A correctly mined and signed chain validates as intact")
    void validChainReturnsTrue() {
        buildValidChain();

        assertThat(service.validateChain(ELECTION_ID)).isTrue();
        assertThat(savedBlocks).hasSize(2);
        assertThat(savedBlocks.get(1).getPreviousHash())
                .isEqualTo(savedBlocks.get(0).getBlockHash());
    }

    @Test
    @DisplayName("Rule 2: tampering a block's data breaks hash integrity")
    void tamperedDataIsDetected() {
        buildValidChain();

        // Mutate a hashed field of the genesis block -> recomputed hash no longer matches.
        savedBlocks.get(0).setMerkleRoot("tampered-merkle-root");

        assertThat(service.validateChain(ELECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("Rule 1: breaking the previousHash link is detected")
    void brokenChainLinkIsDetected() {
        buildValidChain();

        savedBlocks.get(1).setPreviousHash("00deadbeefdeadbeef");

        assertThat(service.validateChain(ELECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("Rule 3: a forged validator signature is detected (PoA)")
    void forgedSignatureIsDetected() {
        buildValidChain();

        String bogus = Base64.getEncoder().encodeToString("not-a-real-signature".getBytes());
        savedBlocks.get(1).setValidatorSignature(bogus);

        assertThat(service.validateChain(ELECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("An election with no blocks is not a valid chain")
    void emptyChainReturnsFalse() {
        when(blockRepository.findByElectionIdOrderByBlockNumberAsc(ELECTION_ID))
                .thenReturn(new ArrayList<>());

        assertThat(service.validateChain(ELECTION_ID)).isFalse();
    }

    @Test
    @DisplayName("verifyVote returns a failure response when the vote hash is unknown")
    void verifyVoteNotFound() {
        when(voteRepository.findByVoteHash("does-not-exist")).thenReturn(Optional.empty());

        VerificationResponse response = service.verifyVote("does-not-exist");

        assertThat(response).isNotNull();
        assertThat(response.isVerified()).isFalse();
        assertThat(response.getMessage()).contains("not found");
    }

    @Test
    @DisplayName("verifyVote returns block info and a Merkle proof for a mined vote")
    void verifyVoteReturnsMerkleProof() {
        buildValidChain();
        Vote mined = votes.get(0);                       // now has blockNumber + currentBlockHash

        when(voteRepository.findByVoteHash(mined.getVoteHash())).thenReturn(Optional.of(mined));
        when(blockRepository.findBlockContainingVote(eq(ELECTION_ID), eq(mined.getVoteHash())))
                .thenReturn(Optional.of(savedBlocks.get(1)));

        VerificationResponse response = service.verifyVote(mined.getVoteHash());

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getBlockNumber()).isEqualTo(1L);
        assertThat(response.getMerkleProof()).isNotBlank();
        assertThat(response.isBlockchainConsistent()).isTrue();
    }

    @Test
    @DisplayName("Block and transaction counters delegate to the repository")
    void statsAreReported() {
        when(blockRepository.countBlocksByElection(ELECTION_ID)).thenReturn(2L);
        when(blockRepository.getTotalTransactionCount(ELECTION_ID)).thenReturn(3L);

        assertThat(service.getBlockCount(ELECTION_ID)).isEqualTo(2L);
        assertThat(service.getTotalTransactions(ELECTION_ID)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Total transactions defaults to 0 when the SUM query returns null")
    void totalTransactionsNullSafe() {
        when(blockRepository.getTotalTransactionCount(ELECTION_ID)).thenReturn(null);

        assertThat(service.getTotalTransactions(ELECTION_ID)).isZero();
    }
}
