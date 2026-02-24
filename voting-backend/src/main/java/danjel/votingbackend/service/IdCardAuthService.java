package danjel.votingbackend.service;

import danjel.votingbackend.dto.AuthResponse;
import danjel.votingbackend.dto.IdCardAuthRequest;
import danjel.votingbackend.exception.AuthenticationException;
import danjel.votingbackend.exception.FaceVerificationException;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import danjel.votingbackend.utils.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles ID-card-based voter authentication.
 *
 * Full flow handled by this service:
 *   Android  →  NFC chip data + two face images  →  POST /api/v1/auth/id-card
 *   Backend  →  1. Validate liveness confirmation
 *               2. Validate chip data (age, expiry, region)
 *               3. Call DeepFaceClient → Python server (internal, never public)
 *               4. Validate face match result
 *               5. Rate-limit failed attempts per nationalId
 *               6. Auto-register new voter OR sync returning voter
 *               7. Issue JWT
 */
@Service
public class IdCardAuthService {

    private static final Logger logger = LoggerFactory.getLogger(IdCardAuthService.class);

    private final VoterRepository voterRepository;
    private final JwtService      jwtService;
    private final DeepFaceClient  deepFaceClient;

    /**
     * Max consecutive face verification failures per nationalId before
     * the backend temporarily refuses new attempts (rate limiting).
     */
    @Value("${deepface.max-attempts:5}")
    private int maxAttempts;

    /**
     * Minutes to lock out a nationalId after maxAttempts failures.
     */
    @Value("${deepface.lockout-minutes:15}")
    private int lockoutMinutes;

    /**
     * Per-nationalId attempt tracking.
     * Key: nationalId, Value: attempt record { count, firstAttemptTime }
     */
    private final Map<String, AttemptRecord> attemptTracker = new ConcurrentHashMap<>();

    public IdCardAuthService(VoterRepository voterRepository,
                             JwtService jwtService,
                             DeepFaceClient deepFaceClient) {
        this.voterRepository = voterRepository;
        this.jwtService      = jwtService;
        this.deepFaceClient  = deepFaceClient;
    }

    @Transactional
    public AuthResponse authenticateWithIdCard(IdCardAuthRequest request, String deviceId) {

        if (deviceId == null || deviceId.isBlank()) {
            throw new AuthenticationException("Missing X-Device-ID");
        }
        // ── 1. Liveness check must pass before we do anything else ───────────
        if (Boolean.FALSE.equals(request.getLivenessConfirmed())) {
            throw new FaceVerificationException(
                    "Liveness check failed. Please ensure you are looking at the camera " +
                            "and follow the on-screen instructions.");
        }

        // ── 2. Validate chip data (server-side, always) ───────────────────────
        validateChipData(request);

        // ── 3. Check rate limiting before calling DeepFace ────────────────────
        checkRateLimit(request.getNationalId());

        // ── 4. Call Python DeepFace server (internal — never public internet) ─
        //    DeepFaceClient throws DeepFaceUnavailableException if server is down,
        //    FaceVerificationException if images are bad or no face detected.
        DeepFaceClient.DeepFaceResult faceResult = deepFaceClient.verify(
                request.getChipFacePhoto(),
                request.getLiveSelfie()
        );

        // ── 5. Handle face match result ───────────────────────────────────────
        if (!faceResult.verified()) {
            recordFailedAttempt(request.getNationalId());

            int remaining = getRemainingAttempts(request.getNationalId());
            String message = remaining > 0
                    ? String.format(
                    "Face does not match ID card photo (distance=%.3f, threshold=%.3f). " +
                            "You have %d attempt(s) remaining. " +
                            "Ensure good lighting and remove glasses if worn.",
                    faceResult.distance(), faceResult.threshold(), remaining)
                    : "Too many failed face verification attempts. Please wait " +
                    lockoutMinutes + " minutes before trying again.";

            throw new FaceVerificationException(message);
        }

        // ── 6. Face matched — clear any previous failed attempts ──────────────
        clearAttempts(request.getNationalId());

        // ── 7. Register new voter or load and sync existing voter ─────────────
        Voter voter = voterRepository.findByNationalId(request.getNationalId())
                .map(existing -> syncFromChip(existing, request))
                .orElseGet(() -> createFromChip(request));

        if (!voter.isEnabled()) {
            throw new AuthenticationException(
                    "This voter account has been disabled. Please contact the election commission.");
        }

        voter.setLastAuthenticatedAt(LocalDateTime.now());
        voterRepository.save(voter);

        // ── 8. Issue JWT ──────────────────────────────────────────────────────
        UserDetails userDetails = buildUserDetails(voter);
        String accessToken  = jwtService.generateToken(buildClaims(voter, faceResult, deviceId), userDetails);
        String refreshToken = jwtService.generateRefreshToken(
                Map.of("userType", "VOTER", "deviceId", deviceId), userDetails);

        AuthResponse response = AuthResponse.success(
                accessToken, refreshToken, jwtService.getExpirationTime());
        response.setVoterId(voter.getId());
        response.setFullName(voter.getFullName());
        response.setRole(UserRole.VOTER);
        response.setVerified(true);
        response.setFaceVerified(true);

        logger.info("Voter authenticated  id={}  name={}  county={}  faceDistance={}",
                voter.getId(), voter.getFullName(), voter.getCounty(), faceResult.distance());

        return response;
    }

    // ── Chip validation ───────────────────────────────────────────────────────

    private void validateChipData(IdCardAuthRequest req) {
        if (req.getDateOfBirth().isAfter(LocalDate.now().minusYears(18))) {
            throw new AuthenticationException(
                    "You must be at least 18 years old to vote.");
        }

        if (req.getExpiryDate().isBefore(LocalDate.now())) {
            throw new AuthenticationException(
                    "Your ID card expired on " + req.getExpiryDate() +
                            ". Please renew it before voting.");
        }

        if (req.getMunicipality() != null && req.getCounty() != null &&
                req.getMunicipality().getCounty() != req.getCounty()) {
            throw new AuthenticationException(
                    "Municipality " + req.getMunicipality().name() +
                            " does not belong to county " + req.getCounty().name() + ".");
        }
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    private void checkRateLimit(String nationalId) {
        AttemptRecord record = attemptTracker.get(nationalId);
        if (record == null) return;

        // Reset window if lockout period has passed
        if (record.firstAttemptTime.plusMinutes(lockoutMinutes).isBefore(LocalDateTime.now())) {
            attemptTracker.remove(nationalId);
            return;
        }

        if (record.count.get() >= maxAttempts) {
            long minutesLeft = java.time.Duration.between(
                    LocalDateTime.now(),
                    record.firstAttemptTime.plusMinutes(lockoutMinutes)
            ).toMinutes() + 1;

            throw new FaceVerificationException(
                    "Too many failed face verification attempts. " +
                            "Please wait " + minutesLeft + " minute(s) before trying again.");
        }
    }

    private void recordFailedAttempt(String nationalId) {
        attemptTracker.compute(nationalId, (id, existing) -> {
            if (existing == null) {
                return new AttemptRecord(1, LocalDateTime.now());
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int count = attemptTracker.get(nationalId).count.get();
        logger.warn("Face verification failed  nationalId={}  attempts={}", nationalId, count);
    }

    private void clearAttempts(String nationalId) {
        attemptTracker.remove(nationalId);
    }

    private int getRemainingAttempts(String nationalId) {
        AttemptRecord record = attemptTracker.get(nationalId);
        if (record == null) return maxAttempts - 1;
        return Math.max(0, maxAttempts - record.count.get());
    }

    // ── Voter lifecycle ───────────────────────────────────────────────────────

    private Voter createFromChip(IdCardAuthRequest req) {
        String[] nameParts = req.getName().split(" ");
        String firstName = nameParts[0];
        String lastName = nameParts[1];
        Voter voter = new Voter(
                req.getNationalId(),
                firstName,
                lastName,
                req.getDateOfBirth(),
                req.getExpiryDate(),
                req.getMunicipality().getCounty(),
                req.getMunicipality(),
                req.getDevicePublicKey()
        );
        Voter saved = voterRepository.save(voter);
        logger.info("New voter registered  nationalId={}  name={}  county={}",
                req.getNationalId(), saved.getFullName(), saved.getCounty());
        return saved;
    }

    /**
     * Sync fields that legitimately change between elections:
     *   - Card renewal (new expiry date)
     *   - Change of address (new county / municipality)
     * Voting history (votedElectionIds) is never touched here.
     */
    private Voter syncFromChip(Voter existing, IdCardAuthRequest req) {
        boolean changed = false;

        if (req.getExpiryDate() != null &&
                !req.getExpiryDate().equals(existing.getCardExpiryDate())) {
            existing.setCardExpiryDate(req.getExpiryDate());
            changed = true;
        }
        if (!req.getMunicipality().getCounty().equals(existing.getCounty())) {
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
                "",   // no password — biometric ID card is the credential
                voter.isEnabled(),
                true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_VOTER"))
        );
    }

    private Map<String, Object> buildClaims(Voter voter, DeepFaceClient.DeepFaceResult faceResult, String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "VOTER");
        claims.put("voterId", voter.getId().toString());
        claims.put("role", UserRole.VOTER.name());
        claims.put("county", voter.getMunicipality().getCounty().name());
        claims.put("municipality", voter.getMunicipality().name());
        claims.put("fullName", voter.getFullName());
        claims.put("faceDistance", faceResult.distance());
        claims.put("deviceId", deviceId);

        return claims;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static class AttemptRecord {
        final AtomicInteger count;
        final LocalDateTime firstAttemptTime;

        AttemptRecord(int initialCount, LocalDateTime firstAttemptTime) {
            this.count            = new AtomicInteger(initialCount);
            this.firstAttemptTime = firstAttemptTime;
        }
    }
}