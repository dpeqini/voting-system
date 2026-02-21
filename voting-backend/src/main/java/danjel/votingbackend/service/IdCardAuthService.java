package danjel.votingbackend.service;

import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.dto.IdCardAuthRequest;
import danjel.votingbackend.exception.AuthenticationException;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles ID-card-based voter authentication.
 *
 * The Android app has already completed the full biometric pipeline before
 * calling this service:
 *   - NFC chip read + chip signature validation (on-device)
 *   - ML Kit liveness detection (on-device)
 *   - Face comparison via Python DeepFace server (Android → Python directly)
 *
 * This service only:
 *   1. Checks that verificationPassed = true
 *   2. Validates chip data (age ≥ 18, card not expired, municipality ↔ county)
 *   3. Auto-registers new voter OR loads + syncs returning voter
 *   4. Issues a JWT session token
 */
@Service
public class IdCardAuthService {

    private static final Logger logger = LoggerFactory.getLogger(IdCardAuthService.class);

    private final VoterRepository voterRepository;
    private final JwtService      jwtService;

    public IdCardAuthService(VoterRepository voterRepository, JwtService jwtService) {
        this.voterRepository = voterRepository;
        this.jwtService      = jwtService;
    }

    @Transactional
    public AuthResponse authenticateWithIdCard(IdCardAuthRequest request) {

        // ── 1. Android must have passed verification before sending this ───────
        if (Boolean.FALSE.equals(request.getVerificationPassed())) {
            throw new AuthenticationException(
                    "Biometric verification did not pass. Please complete face verification first.");
        }

        // ── 2. Validate chip data server-side ─────────────────────────────────
        validateChipData(request);

        // ── 3. Auto-register or load voter ────────────────────────────────────
        Voter voter = voterRepository.findByNationalId(request.getNationalId())
                .map(existing -> syncFromChip(existing, request))
                .orElseGet(() -> createFromChip(request));

        if (!voter.isEnabled()) {
            throw new AuthenticationException(
                    "This voter account has been disabled. Please contact the election commission.");
        }

        voter.setLastAuthenticatedAt(LocalDateTime.now());
        voterRepository.save(voter);

        // ── 4. Issue JWT ──────────────────────────────────────────────────────
        UserDetails userDetails = buildUserDetails(voter);
        String accessToken  = jwtService.generateToken(buildClaims(voter), userDetails);
        String refreshToken = jwtService.generateRefreshToken(
                Map.of("userType", "VOTER"), userDetails);

        AuthResponse response = AuthResponse.success(
                accessToken, refreshToken, jwtService.getExpirationTime());
        response.setVoterId(voter.getId());
        response.setFullName(voter.getFullName());
        response.setRole(UserRole.VOTER);
        response.setVerified(true);
        response.setFaceVerified(true);

        logger.info("Voter authenticated  id={}  name={}  county={}",
                voter.getId(), voter.getFullName(), voter.getCounty());

        return response;
    }

    // ── Chip validation ───────────────────────────────────────────────────────

    private void validateChipData(IdCardAuthRequest req) {
        if (req.getDateOfBirth().isAfter(LocalDate.now().minusYears(18))) {
            throw new AuthenticationException(
                    "You must be at least 18 years old to vote.");
        }

        if (req.getCardExpiryDate().isBefore(LocalDate.now())) {
            throw new AuthenticationException(
                    "Your ID card expired on " + req.getCardExpiryDate() +
                            ". Please renew it before voting.");
        }

        if (req.getMunicipality() != null && req.getCounty() != null &&
                req.getMunicipality().getCounty() != req.getCounty()) {
            throw new AuthenticationException(
                    "Municipality does not belong to the specified county.");
        }
    }

    // ── Voter lifecycle ───────────────────────────────────────────────────────

    private Voter createFromChip(IdCardAuthRequest req) {
        Voter voter = new Voter(
                req.getNationalId(),
                req.getFirstName(),
                req.getLastName(),
                req.getDateOfBirth(),
                req.getCardExpiryDate(),
                req.getCounty(),
                req.getMunicipality()
        );
        Voter saved = voterRepository.save(voter);
        logger.info("New voter registered  nationalId={}  name={}",
                req.getNationalId(), saved.getFullName());
        return saved;
    }

    /**
     * Sync fields that legitimately change between elections:
     * card renewal (new expiry), address change (county/municipality).
     * Voting history is never modified here.
     */
    private Voter syncFromChip(Voter existing, IdCardAuthRequest req) {
        boolean changed = false;

        if (!req.getCardExpiryDate().equals(existing.getCardExpiryDate())) {
            existing.setCardExpiryDate(req.getCardExpiryDate());
            changed = true;
        }
        if (!req.getCounty().equals(existing.getCounty())) {
            existing.setCounty(req.getCounty());
            changed = true;
        }
        if (!req.getMunicipality().equals(existing.getMunicipality())) {
            existing.setMunicipality(req.getMunicipality());
            changed = true;
        }

        if (changed) {
            logger.info("Voter chip data updated  nationalId={}", req.getNationalId());
        }
        return existing;
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private UserDetails buildUserDetails(Voter voter) {
        return new User(
                voter.getNationalId(),
                "",   // no password — ID card is the credential
                voter.isEnabled(),
                true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_VOTER"))
        );
    }

    private Map<String, Object> buildClaims(Voter voter) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType",     "VOTER");
        claims.put("voterId",      voter.getId());
        claims.put("role",         UserRole.VOTER.name());
        claims.put("county",       voter.getCounty().name());
        claims.put("municipality", voter.getMunicipality().name());
        claims.put("fullName",     voter.getFullName());
        return claims;
    }
}