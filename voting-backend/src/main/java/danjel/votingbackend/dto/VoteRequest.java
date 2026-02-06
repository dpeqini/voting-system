package danjel.votingbackend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VoteRequest {

    // Getters and Setters
    @NotBlank(message = "Election ID is required")
    private String electionId;

    private String candidateId;

    private String partyId;

    @NotBlank(message = "Encrypted vote data is required")
    private String encryptedVoteData;

    @NotBlank(message = "Digital signature is required")
    private String digitalSignature;

    private String nonce;

    private String faceVerificationToken;

    private String votingStationId;

    // Constructors
    public VoteRequest() {}

    public VoteRequest(String electionId, String candidateId, String encryptedVoteData,
                       String digitalSignature) {
        this.electionId = electionId;
        this.candidateId = candidateId;
        this.encryptedVoteData = encryptedVoteData;
        this.digitalSignature = digitalSignature;
    }

}
