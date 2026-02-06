package danjel.votingbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class FaceVerificationResponse {

    // Getters and Setters
    private boolean verified;
    private double confidenceScore;
    private String verificationToken;
    private LocalDateTime tokenExpiry;
    private boolean livenessChecked;
    private boolean livenessVerified;
    private String message;
    private String errorCode;

    // Constructors
    public FaceVerificationResponse() {}

    public FaceVerificationResponse(boolean verified, String message) {
        this.verified = verified;
        this.message = message;
    }

    public static FaceVerificationResponse success(double confidenceScore, String verificationToken) {
        FaceVerificationResponse response = new FaceVerificationResponse(true, "Face verification successful");
        response.setConfidenceScore(confidenceScore);
        response.setVerificationToken(verificationToken);
        response.setTokenExpiry(LocalDateTime.now().plusMinutes(15));
        return response;
    }

    public static FaceVerificationResponse failure(String message, String errorCode) {
        FaceVerificationResponse response = new FaceVerificationResponse(false, message);
        response.setErrorCode(errorCode);
        return response;
    }

}