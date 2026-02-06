package danjel.votingbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class VoteResponse {

    // Getters and Setters
    private String voteId;
    private String voteHash;
    private String blockchainTransactionId;
    private Long blockNumber;
    private LocalDateTime timestamp;
    private String verificationCode;
    private boolean success;
    private String message;
    private String receiptToken;

    // Constructors
    public VoteResponse() {}

    public VoteResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static VoteResponse success(String voteId, String voteHash, String transactionId) {
        VoteResponse response = new VoteResponse(true, "Vote cast successfully");
        response.setVoteId(voteId);
        response.setVoteHash(voteHash);
        response.setBlockchainTransactionId(transactionId);
        return response;
    }

    public static VoteResponse failure(String message) {
        return new VoteResponse(false, message);
    }

}
