package danjel.votingbackend.dto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class VerificationResponse {

    // Getters and Setters
    private boolean verified;
    private String voteHash;
    private String blockchainTransactionId;
    private Long blockNumber;
    private String blockHash;
    private LocalDateTime voteTimestamp;
    private LocalDateTime verificationTimestamp;
    private String merkleProof;
    private boolean blockchainConsistent;
    private String message;
    private String electionId;
    private String electionName;

    // Constructors
    public VerificationResponse() {
        this.verificationTimestamp = LocalDateTime.now();
    }

    public VerificationResponse(boolean verified, String message) {
        this();
        this.verified = verified;
        this.message = message;
    }

    public static VerificationResponse success(String voteHash, String transactionId, Long blockNumber) {
        VerificationResponse response = new VerificationResponse(true, "Vote verified successfully");
        response.setVoteHash(voteHash);
        response.setBlockchainTransactionId(transactionId);
        response.setBlockNumber(blockNumber);
        response.setBlockchainConsistent(true);
        return response;
    }

    public static VerificationResponse failure(String message) {
        return new VerificationResponse(false, message);
    }

}
