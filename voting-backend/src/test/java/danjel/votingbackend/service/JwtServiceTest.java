package danjel.votingbackend.service;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT issuance and validation (JwtService), Kreu VI.
 *
 * Covers token generation with custom voter claims, subject/claim extraction,
 * validity checking against a UserDetails, and expiry behaviour.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails voter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // HS256 requires a >= 256-bit key; secret is stored Base64-encoded.
        String rawSecret = "0123456789012345678901234567890123456789"; // 40 bytes
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes());

        ReflectionTestUtils.setField(jwtService, "secretKey", base64Secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);      // 1h
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L); // 7d

        voter = User.withUsername("AL1234567")
                .password("n/a")
                .authorities("ROLE_VOTER")
                .build();
    }

    private Map<String, Object> voterClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "VOTER");
        claims.put("voterId", "voter-uuid-123");
        claims.put("role", "VOTER");
        claims.put("county", "TIRANE");
        claims.put("deviceId", "device-abc-9");
        return claims;
    }

    @Test
    @DisplayName("A generated token carries the subject and all custom voter claims")
    void tokenCarriesSubjectAndClaims() {
        String token = jwtService.generateToken(voterClaims(), voter);

        assertThat(jwtService.extractUsername(token)).isEqualTo("AL1234567");
        assertThat(jwtService.extractUserType(token)).isEqualTo("VOTER");
        assertThat(jwtService.extractVoterId(token)).isEqualTo("voter-uuid-123");
        assertThat(jwtService.extractRole(token)).isEqualTo("VOTER");
        assertThat(jwtService.extractCounty(token)).isEqualTo("TIRANE");
        assertThat(jwtService.extractDeviceId(token)).isEqualTo("device-abc-9");
    }

    @Test
    @DisplayName("A token is valid for its own user and invalid for a different user")
    void tokenValidityIsBoundToUser() {
        String token = jwtService.generateToken(voterClaims(), voter);

        assertThat(jwtService.isTokenValid(token, voter)).isTrue();

        UserDetails other = User.withUsername("AL9999999")
                .password("n/a").authorities("ROLE_VOTER").build();
        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    @DisplayName("A missing custom claim extracts as null")
    void missingClaimIsNull() {
        String token = jwtService.generateToken(new HashMap<>(), voter);

        assertThat(jwtService.extractDeviceId(token)).isNull();
        assertThat(jwtService.extractVoterId(token)).isNull();
    }

    @Test
    @DisplayName("Parsing an expired token throws ExpiredJwtException")
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -10_000L); // already expired
        String expired = jwtService.generateToken(voterClaims(), voter);

        assertThatThrownBy(() -> jwtService.isTokenValid(expired, voter))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("A refresh token also carries the subject and is parseable")
    void refreshTokenIsIssued() {
        String refresh = jwtService.generateRefreshToken(voterClaims(), voter);

        assertThat(jwtService.extractUsername(refresh)).isEqualTo("AL1234567");
        assertThat(jwtService.extractVoterId(refresh)).isEqualTo("voter-uuid-123");
    }
}
