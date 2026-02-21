package danjel.votingbackend.service;

import danjel.votingbackend.config.BlockchainConfig;
import danjel.votingbackend.dto.VerificationResponse;
import danjel.votingbackend.exception.BlockchainException;
import danjel.votingbackend.model.Block;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Vote;
import danjel.votingbackend.repository.BlockRepository;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class BlockchainService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainService.class);

    private final BlockRepository    blockRepository;
    private final VoteRepository     voteRepository;
    private final ElectionRepository electionRepository;
    private final BlockchainConfig   blockchainConfig;
    private final KeyPair            serverKeyPair;

    private final Map<String, Queue<Vote>> pendingVotes  = new ConcurrentHashMap<>();
    private final Map<String, Object>      electionLocks = new ConcurrentHashMap<>();

    private static final String GENESIS_PREVIOUS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    public BlockchainService(BlockRepository blockRepository,
                             VoteRepository voteRepository,
                             ElectionRepository electionRepository,
                             BlockchainConfig blockchainConfig,
                             KeyPair serverKeyPair) {
        this.blockRepository    = blockRepository;
        this.voteRepository     = voteRepository;
        this.electionRepository = electionRepository;
        this.blockchainConfig   = blockchainConfig;
        this.serverKeyPair      = serverKeyPair;
    }

    // ── Blockchain init ───────────────────────────────────────────────────────

    @Transactional
    public String initializeBlockchain(Election election) {
        Block genesisBlock = new Block(0L, GENESIS_PREVIOUS_HASH, election);
        genesisBlock.setMerkleRoot(calculateMerkleRoot(Collections.emptyList()));

        String blockHash = mineBlock(genesisBlock);
        genesisBlock.setBlockHash(blockHash);
        genesisBlock.setValidated(true);
        genesisBlock.setValidatedAt(LocalDateTime.now());
        genesisBlock.setValidatorSignature(signBlock(blockHash));

        blockRepository.save(genesisBlock);

        election.setGenesisBlockHash(blockHash);
        election.setBlockchainContractAddress(generateContractAddress(election.getId()));
        electionRepository.save(election);

        pendingVotes.put(election.getId(), new ConcurrentLinkedQueue<>());
        electionLocks.put(election.getId(), new Object());

        logger.info("Blockchain initialised  election={}  genesis={}", election.getId(), blockHash);
        return blockHash;
    }

    // ── Vote ingestion ────────────────────────────────────────────────────────

    @Transactional
    public String addVoteToBlockchain(Vote vote) {
        String electionId = vote.getElection().getId();

        Queue<Vote> electionPendingVotes = pendingVotes.computeIfAbsent(
                electionId, k -> new ConcurrentLinkedQueue<>());

        electionPendingVotes.add(vote);

        String transactionId = generateTransactionId(vote);

        if (electionPendingVotes.size() >= blockchainConfig.getBlockSize()) {
            processBlockAsync(electionId);
        }

        return transactionId;
    }

    @Async("blockchainTaskExecutor")
    public void processBlockAsync(String electionId) {
        Object lock = electionLocks.computeIfAbsent(electionId, k -> new Object());
        synchronized (lock) {
            processBlock(electionId);
        }
    }

    @Transactional
    public void processBlock(String electionId) {
        Queue<Vote> electionPendingVotes = pendingVotes.get(electionId);
        if (electionPendingVotes == null || electionPendingVotes.isEmpty()) return;

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new BlockchainException("Election not found"));

        Block latestBlock = blockRepository.findLatestBlock(electionId)
                .orElseThrow(() -> new BlockchainException("No genesis block found"));

        List<Vote> votesToProcess = new ArrayList<>();
        while (!electionPendingVotes.isEmpty()
                && votesToProcess.size() < blockchainConfig.getBlockSize()) {
            Vote vote = electionPendingVotes.poll();
            if (vote != null) votesToProcess.add(vote);
        }

        if (votesToProcess.isEmpty()) return;

        Block newBlock = new Block(
                latestBlock.getBlockNumber() + 1,
                latestBlock.getBlockHash(),
                election);

        List<String> voteHashes = new ArrayList<>();
        for (Vote vote : votesToProcess) {
            voteHashes.add(vote.getVoteHash());
            newBlock.addVoteHash(vote.getVoteHash());
        }

        newBlock.setMerkleRoot(calculateMerkleRoot(voteHashes));

        String blockHash = mineBlock(newBlock);
        newBlock.setBlockHash(blockHash);
        newBlock.setValidated(true);
        newBlock.setValidatedAt(LocalDateTime.now());
        newBlock.setValidatorSignature(signBlock(blockHash));

        blockRepository.save(newBlock);

        for (Vote vote : votesToProcess) {
            vote.setBlockNumber(newBlock.getBlockNumber());
            vote.setPreviousBlockHash(latestBlock.getBlockHash());
            vote.setCurrentBlockHash(blockHash);
            vote.setVerified(true);
            vote.setVerifiedAt(LocalDateTime.now());
            voteRepository.save(vote);
        }

        logger.info("Block {}  election={}  votes={}",
                newBlock.getBlockNumber(), electionId, votesToProcess.size());
    }

    // ── Vote verification ─────────────────────────────────────────────────────

    /**
     * Look up a vote by its hash and build a verification response.
     *
     * NOTE: We deliberately do NOT call validateChain() here.
     * Full chain validation walks every block in the election — expensive on every
     * voter lookup. The chain integrity is instead verified separately via
     * GET /verification/blockchain/{electionId}/validate (admin/audit use).
     * We still check that the vote's own stored block hash is internally consistent.
     */
    public VerificationResponse verifyVote(String voteHash) {
        Vote vote = voteRepository.findByVoteHash(voteHash).orElse(null);
        if (vote == null) {
            return VerificationResponse.failure("Vote not found with hash: " + voteHash);
        }
        return buildVerificationResponse(vote);
    }

    /**
     * Look up a vote by the receipt token the voter received after casting.
     *
     * The receipt token is a SHA-256 of (voteId + voteHash + timestamp),
     * stored on the Vote entity and returned in VoteResponse.receiptToken.
     * It is distinct from the voteHash — it lets the voter confirm their vote
     * without needing to know or store the longer voteHash.
     */
    public VerificationResponse verifyVoteByReceipt(String receiptToken) {
        Vote vote = voteRepository.findByReceiptToken(receiptToken).orElse(null);
        if (vote == null) {
            return VerificationResponse.failure("Vote not found with receipt token: " + receiptToken);
        }
        return buildVerificationResponse(vote);
    }

    /**
     * Shared response builder used by both verify methods.
     * Attaches block info and Merkle proof when the vote has been mined into a block.
     * Does NOT run full chain validation (see verifyVote for rationale).
     */
    private VerificationResponse buildVerificationResponse(Vote vote) {
        VerificationResponse response = VerificationResponse.success(
                vote.getVoteHash(),
                vote.getBlockchainTransactionId(),
                vote.getBlockNumber()
        );

        response.setBlockHash(vote.getCurrentBlockHash());
        response.setVoteTimestamp(vote.getTimestamp());
        response.setElectionId(vote.getElection().getId());
        response.setElectionName(vote.getElection().getName());
        response.setVerified(vote.isVerified());

        // Merkle proof — only available after the vote has been mined into a block
        if (vote.getBlockNumber() != null && vote.getCurrentBlockHash() != null) {
            blockRepository
                    .findBlockContainingVote(vote.getElection().getId(), vote.getVoteHash())
                    .ifPresent(block -> {
                        response.setMerkleProof(generateMerkleProof(block, vote.getVoteHash()));
                        // Lightweight consistency check: does the block's hash still compute?
                        String recomputed = calculateBlockHash(block);
                        response.setBlockchainConsistent(recomputed.equals(block.getBlockHash()));
                    });
        } else {
            // Vote is still in the pending queue, not yet mined
            response.setBlockchainConsistent(false);
            response.setMessage("Vote recorded but not yet committed to a block. Check again shortly.");
        }

        return response;
    }

    // ── Chain validation ──────────────────────────────────────────────────────

    public boolean validateChain(String electionId) {
        List<Block> blocks = blockRepository.findByElectionIdOrderByBlockNumberAsc(electionId);
        if (blocks.isEmpty()) return false;

        Block previousBlock = null;
        for (Block block : blocks) {
            if (previousBlock != null &&
                    !block.getPreviousHash().equals(previousBlock.getBlockHash())) {
                logger.error("Chain broken at block {}", block.getBlockNumber());
                return false;
            }
            if (!calculateBlockHash(block).equals(block.getBlockHash())) {
                logger.error("Hash mismatch at block {}", block.getBlockNumber());
                return false;
            }
            previousBlock = block;
        }
        return true;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public long getBlockCount(String electionId) {
        return blockRepository.countBlocksByElection(electionId);
    }

    public Long getTotalTransactions(String electionId) {
        Long total = blockRepository.getTotalTransactionCount(electionId);
        return total != null ? total : 0L;  // SUM returns null when there are no rows
    }

    public void flushPendingVotes(String electionId) {
        Queue<Vote> pending = pendingVotes.get(electionId);
        if (pending != null && !pending.isEmpty()) {
            processBlock(electionId);
        }
    }

    // ── Mining / hashing / crypto ─────────────────────────────────────────────

    private String mineBlock(Block block) {
        String prefix = "0".repeat(blockchainConfig.getDifficulty());
        long nonce = 0;
        String hash;
        do {
            nonce++;
            block.setNonce(nonce);
            hash = calculateBlockHash(block);
        } while (!hash.startsWith(prefix));
        return hash;
    }

    private String calculateBlockHash(Block block) {
        try {
            MessageDigest digest = MessageDigest.getInstance(blockchainConfig.getHashAlgorithm());
            String input = block.getBlockNumber()
                    + block.getPreviousHash()
                    + block.getMerkleRoot()
                    + block.getTimestamp().toString()
                    + block.getNonce();
            return bytesToHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BlockchainException("Hash algorithm not available");
        }
    }

    private String calculateMerkleRoot(List<String> voteHashes) {
        if (voteHashes.isEmpty()) return hash("EMPTY_MERKLE_ROOT");

        List<String> hashes = new ArrayList<>(voteHashes);
        while (hashes.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                String left  = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                next.add(hash(left + right));
            }
            hashes = next;
        }
        return hashes.get(0);
    }

    private String generateMerkleProof(Block block, String voteHash) {
        List<String> voteHashes = block.getVoteHashes();
        int index = voteHashes.indexOf(voteHash);
        if (index == -1) return null;

        StringBuilder proof = new StringBuilder();
        List<String> currentLevel = new ArrayList<>(voteHashes);

        while (currentLevel.size() > 1) {
            int siblingIndex = (index % 2 == 0) ? index + 1 : index - 1;
            if (siblingIndex < currentLevel.size()) {
                proof.append(currentLevel.get(siblingIndex)).append(";");
            }

            List<String> next = new ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left  = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                next.add(hash(left + right));
            }
            index = index / 2;
            currentLevel = next;
        }
        return proof.toString();
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(blockchainConfig.getHashAlgorithm());
            return bytesToHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BlockchainException("Hash algorithm not available");
        }
    }

    private String signBlock(String blockHash) {
        try {
            Signature sig = Signature.getInstance(blockchainConfig.getSignatureAlgorithm());
            sig.initSign(serverKeyPair.getPrivate());
            sig.update(blockHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new BlockchainException("Failed to sign block: " + e.getMessage());
        }
    }

    private String generateTransactionId(Vote vote) {
        return hash(vote.getVoteHash() + vote.getTimestamp().toString() + UUID.randomUUID());
    }

    private String generateContractAddress(String electionId) {
        return "0x" + hash(electionId + LocalDateTime.now().toString()).substring(0, 40);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}