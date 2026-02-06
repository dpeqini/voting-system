package danjel.votingbackend.dto;

import danjel.votingbackend.utils.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class AuthResponse {

    // Getters and Setters
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String voterId;
    private String email;
    private String fullName;
    private UserRole role;
    private boolean verified;
    private boolean faceVerified;
    private LocalDateTime loginTime;
    private String message;
    private boolean success;

    // Constructors
    public AuthResponse() {
        this.loginTime = LocalDateTime.now();
    }

    public AuthResponse(String accessToken, String refreshToken, long expiresIn) {
        this();
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.success = true;
    }

    public static AuthResponse success(String accessToken, String refreshToken, long expiresIn) {
        AuthResponse response = new AuthResponse(accessToken, refreshToken, expiresIn);
        response.setMessage("Authentication successful");
        return response;
    }

    public static AuthResponse failure(String message) {
        AuthResponse response = new AuthResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

}
