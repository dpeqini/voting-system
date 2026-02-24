package danjel.votingbackend.service;

import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.exception.AuthenticationException;
import danjel.votingbackend.model.Admin;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.AdminRepository;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security UserDetailsService + JWT refresh for both voters and admins.
 *
 * ── Voter auth ────────────────────────────────────────────────────────────────
 * Voters authenticate exclusively via ID card (NFC + ML Kit + DeepFace).
 * That pipeline is handled entirely by {@link IdCardAuthService}.
 *
 * The JWT subject for a voter is their NATIONAL ID (not email, not DB uuid).
 * Reason: nationalId is the stable, chip-verified identity; there is no email
 * or password on the Voter entity anymore.
 *
 * So every method that resolves a voter from a JWT subject uses
 * {@code findByNationalId}, never {@code findByEmail}.
 *
 * ── Admin auth ────────────────────────────────────────────────────────────────
 * Admins still use email + password via {@link AdminAuthService}.
 * The JWT subject for an admin is their email, and userType claim = "ADMIN".
 * This path is unchanged.
 */
@Service
public class AuthService implements UserDetailsService {

    private final VoterRepository voterRepository;
    private final AdminRepository adminRepository;
    private final JwtService      jwtService;

    public AuthService(VoterRepository voterRepository,
                       AdminRepository adminRepository,
                       JwtService jwtService) {
        this.voterRepository = voterRepository;
        this.adminRepository = adminRepository;
        this.jwtService      = jwtService;
    }

    // ── UserDetailsService (called by JwtAuthenticationFilter) ───────────────

    /**
     * Spring Security calls this with the JWT subject.
     * For voters  → subject is nationalId  → look up by nationalId.
     * For admins  → never routed here      → see loadUserByUsernameAndType.
     */
    @Override
    public UserDetails loadUserByUsername(String nationalId) throws UsernameNotFoundException {
        Voter voter = voterRepository.findByNationalId(nationalId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Voter not found with nationalId: " + nationalId));

        return buildVoterUserDetails(voter);
    }

    /**
     * Called by {@link danjel.votingbackend.config.JwtAuthenticationFilter}
     * which reads the {@code userType} claim to decide which table to query.
     */
    public UserDetails loadUserByUsernameAndType(String subject, String userType)
            throws UsernameNotFoundException {

        if ("ADMIN".equals(userType)) {
            Admin admin = adminRepository.findByEmail(subject)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "Admin not found with email: " + subject));

            return new User(
                    admin.getEmail(),
                    admin.getPasswordHash(),
                    admin.isEnabled(),
                    true, true,
                    !admin.isAccountLocked(),
                    Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()))
            );
        }

        // VOTER — subject is nationalId
        return loadUserByUsername(subject);
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    /**
     * Issues a new access token from a valid refresh token.
     * Works for both voters (subject = nationalId) and admins (subject = email).
     */
    public AuthResponse refreshToken(String refreshToken, String deviceId) {
        String subject  = jwtService.extractUsername(refreshToken);
        String userType = jwtService.extractUserType(refreshToken);
        if (userType == null) userType = "VOTER";

        UserDetails userDetails = loadUserByUsernameAndType(subject, userType);

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new AuthenticationException("Invalid or expired refresh token");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", userType);

        if ("VOTER".equals(userType)) {
            Voter voter = voterRepository.findByNationalId(subject)
                    .orElseThrow(() -> new AuthenticationException("Voter not found"));

            if (!voter.isEnabled()) {
                throw new AuthenticationException("Voter account is disabled");
            }

            claims.put("voterId",      voter.getId());
            claims.put("role",         UserRole.VOTER.name());
            claims.put("county",       voter.getCounty().name());
            claims.put("municipality", voter.getMunicipality().name());
            claims.put("fullName",     voter.getFullName());
            claims.put("deviceId",     deviceId);

            String newToken = jwtService.generateToken(claims, userDetails);
            AuthResponse response = AuthResponse.success(
                    newToken, refreshToken, jwtService.getExpirationTime());
            response.setVoterId(voter.getId());
            response.setFullName(voter.getFullName());
            response.setRole(UserRole.VOTER);
            return response;
        }

        // Admin refresh — delegate to AdminAuthService conventions
        String newToken = jwtService.generateToken(claims, userDetails);
        return AuthResponse.success(newToken, refreshToken, jwtService.getExpirationTime());
    }

    // ── Convenience lookup used by controllers ────────────────────────────────

    /**
     * Resolves the currently authenticated voter from the JWT subject.
     *
     * Controllers call this with {@code userDetails.getUsername()} which Spring
     * Security populates from the JWT subject — i.e. the voter's nationalId.
     *
     * @param nationalId the JWT subject (voter's national ID number)
     */
    public Voter getCurrentVoter(String nationalId) {
        return voterRepository.findByNationalId(nationalId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Voter not found with nationalId: " + nationalId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserDetails buildVoterUserDetails(Voter voter) {
        return new User(
                voter.getNationalId(),  // subject = nationalId
                "",                      // no password
                voter.isEnabled(),
                true, true, true,        // non-expired, non-locked — managed by enabled flag only
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_VOTER"))
        );
    }
}