package danjel.votingbackend.dto;

import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class VoteResponse {

    // ── Core vote identity ────────────────────────────────────────────────────

    private boolean success;
    private String message;

    /** Internal UUID of the saved Vote entity. */
    private String voteId;

    /** SHA-256 hash of the vote — used to verify later via blockchain. */
    private String voteHash;

    /** Receipt token for the voter to look up their vote. */
    private String receiptToken;

    /** Short verification code displayed to the voter after casting. */
    private String verificationCode;

    private LocalDateTime timestamp;

    // ── Blockchain anchoring ──────────────────────────────────────────────────

    private String blockchainTransactionId;
    private Long   blockNumber;

    // ── Human-readable confirmation details ──────────────────────────────────

    /** Full name of the candidate voted for (if a specific candidate was chosen). */
    private String candidateName;

    /** Name of the party voted for. */
    private String partyName;

    /** Name of the election. */
    private String electionName;

    /** Voter's county at time of vote (for confirmation display). */
    private AlbanianCounty county;

    /** Voter's municipality at time of vote. */
    private AlbanianMunicipality municipality;

    // ── Constructors ──────────────────────────────────────────────────────────

    public VoteResponse() {}

    public VoteResponse(boolean success, String message) {
        this.success   = success;
        this.message   = message;
        this.timestamp = LocalDateTime.now();
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static VoteResponse success(String voteId, String voteHash, String transactionId) {
        VoteResponse r = new VoteResponse(true, "Vote cast successfully");
        r.setVoteId(voteId);
        r.setVoteHash(voteHash);
        r.setBlockchainTransactionId(transactionId);
        return r;
    }

    public static VoteResponse failure(String message) {
        return new VoteResponse(false, message);
    }
}