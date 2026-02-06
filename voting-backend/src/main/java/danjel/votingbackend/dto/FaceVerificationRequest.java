package danjel.votingbackend.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FaceVerificationRequest {

    // Getters and Setters
    @NotBlank(message = "Face image data is required")
    private String faceImageBase64;

    private String livenessToken;

    private boolean enrollMode = false;

    private String deviceFingerprint;

    private String sessionId;

    // Constructors
    public FaceVerificationRequest() {}

    public FaceVerificationRequest(String faceImageBase64) {
        this.faceImageBase64 = faceImageBase64;
    }

}
