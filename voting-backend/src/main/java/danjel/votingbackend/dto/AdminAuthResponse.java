package danjel.votingbackend.dto;

import danjel.votingbackend.utils.enums.AdminRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class AdminAuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String adminId;
    private String email;
    private String fullName;
    private AdminRole role;
    private LocalDateTime loginTime;
    private String message;
    private boolean success;

    public AdminAuthResponse() {
        this.loginTime = LocalDateTime.now();
    }

    public AdminAuthResponse(String accessToken, String refreshToken, long expiresIn) {
        this();
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.success = true;
    }

    public static AdminAuthResponse success(String accessToken, String refreshToken, long expiresIn) {
        AdminAuthResponse response = new AdminAuthResponse(accessToken, refreshToken, expiresIn);
        response.setMessage("Authentication successful");
        return response;
    }

    public static AdminAuthResponse failure(String message) {
        AdminAuthResponse response = new AdminAuthResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
