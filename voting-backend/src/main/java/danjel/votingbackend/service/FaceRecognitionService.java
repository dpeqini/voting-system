package danjel.votingbackend.service;

import danjel.votingbackend.dto.FaceVerificationRequest;
import danjel.votingbackend.dto.FaceVerificationResponse;
import danjel.votingbackend.exception.FaceVerificationException;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.VoterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionService.class);

    private final VoterRepository voterRepository;

    @Value("${face-recognition.confidence-threshold:0.85}")
    private double confidenceThreshold;

    @Value("${face-recognition.token-validity-minutes:15}")
    private int tokenValidityMinutes;

    @Value("${face-recognition.max-attempts:3}")
    private int maxAttempts;

    private final Map<String, VerificationToken> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, Integer> attemptCounts = new ConcurrentHashMap<>();

    public FaceRecognitionService(VoterRepository voterRepository) {
        this.voterRepository = voterRepository;
    }

    @Transactional
    public FaceVerificationResponse enrollFace(String voterId, FaceVerificationRequest request) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new FaceVerificationException("Voter not found"));

        if (voter.isFaceVerified()) {
            throw new FaceVerificationException("Face already enrolled for this voter");
        }

        byte[] faceImageBytes = Base64.getDecoder().decode(request.getFaceImageBase64());

        // Validate image quality
        if (!validateImageQuality(faceImageBytes)) {
            return FaceVerificationResponse.failure(
                    "Image quality too low. Please ensure good lighting and face visibility.",
                    "LOW_QUALITY"
            );
        }

        // Detect face in image
        byte[] faceEncoding = detectAndEncodeFace(faceImageBytes);
        if (faceEncoding == null) {
            return FaceVerificationResponse.failure(
                    "No face detected in image. Please ensure your face is clearly visible.",
                    "NO_FACE_DETECTED"
            );
        }

        // Perform liveness check if token provided
        if (request.getLivenessToken() != null) {
            if (!performLivenessCheck(request.getLivenessToken(), faceImageBytes)) {
                return FaceVerificationResponse.failure(
                        "Liveness check failed. Please try again with live camera.",
                        "LIVENESS_FAILED"
                );
            }
        }

        // Store face encoding
        voter.setFaceEncodingData(faceEncoding);
        voter.setFaceVerified(true);
        voterRepository.save(voter);

        logger.info("Face enrolled successfully for voter: {}", voterId);

        String verificationToken = generateVerificationToken(voterId);
        return FaceVerificationResponse.success(1.0, verificationToken);
    }

    public FaceVerificationResponse verifyFace(String voterId, FaceVerificationRequest request) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new FaceVerificationException("Voter not found"));

        if (!voter.isFaceVerified() || voter.getFaceEncodingData() == null) {
            throw new FaceVerificationException("Face not enrolled for this voter");
        }

        // Check attempt limits
        int attempts = attemptCounts.getOrDefault(voterId, 0);
        if (attempts >= maxAttempts) {
            return FaceVerificationResponse.failure(
                    "Maximum verification attempts exceeded. Please try again later.",
                    "MAX_ATTEMPTS_EXCEEDED"
            );
        }

        byte[] faceImageBytes = Base64.getDecoder().decode(request.getFaceImageBase64());

        // Detect face in submitted image
        byte[] submittedFaceEncoding = detectAndEncodeFace(faceImageBytes);
        if (submittedFaceEncoding == null) {
            attemptCounts.put(voterId, attempts + 1);
            return FaceVerificationResponse.failure(
                    "No face detected in image.",
                    "NO_FACE_DETECTED"
            );
        }

        // Perform liveness check
        FaceVerificationResponse response = new FaceVerificationResponse();
        response.setLivenessChecked(request.getLivenessToken() != null);

        if (request.getLivenessToken() != null) {
            boolean livenessResult = performLivenessCheck(request.getLivenessToken(), faceImageBytes);
            response.setLivenessVerified(livenessResult);
            if (!livenessResult) {
                attemptCounts.put(voterId, attempts + 1);
                return FaceVerificationResponse.failure(
                        "Liveness check failed.",
                        "LIVENESS_FAILED"
                );
            }
        }

        // Compare face encodings
        double similarity = compareFaceEncodings(voter.getFaceEncodingData(), submittedFaceEncoding);

        if (similarity >= confidenceThreshold) {
            attemptCounts.remove(voterId);
            String verificationToken = generateVerificationToken(voterId);

            response.setVerified(true);
            response.setConfidenceScore(similarity);
            response.setVerificationToken(verificationToken);
            response.setTokenExpiry(LocalDateTime.now().plusMinutes(tokenValidityMinutes));
            response.setMessage("Face verification successful");

            logger.info("Face verified successfully for voter: {} with confidence: {}", voterId, similarity);
            return response;
        } else {
            attemptCounts.put(voterId, attempts + 1);
            return FaceVerificationResponse.failure(
                    "Face verification failed. Please try again.",
                    "VERIFICATION_FAILED"
            );
        }
    }

    public boolean validateVerificationToken(String voterId, String token) {
        VerificationToken storedToken = activeTokens.get(voterId);

        if (storedToken == null) {
            return false;
        }

        if (storedToken.isExpired()) {
            activeTokens.remove(voterId);
            return false;
        }

        boolean valid = storedToken.getToken().equals(token);

        if (valid) {
            activeTokens.remove(voterId);
        }

        return valid;
    }

    private String generateVerificationToken(String voterId) {
        String token = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            token = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // Use UUID as fallback
        }

        VerificationToken verificationToken = new VerificationToken(
                token,
                LocalDateTime.now().plusMinutes(tokenValidityMinutes)
        );
        activeTokens.put(voterId, verificationToken);

        return token;
    }

    private boolean validateImageQuality(byte[] imageBytes) {
        // Check minimum size
        if (imageBytes.length < 10000) {
            return false;
        }
        // In production, would check resolution, blur, lighting, etc.
        return true;
    }

    private byte[] detectAndEncodeFace(byte[] imageBytes) {
        // In production, this would use a face recognition library like:
        // - OpenCV with DNN module
        // - AWS Rekognition
        // - Azure Face API
        // - Google Cloud Vision
        // - Local ML model (dlib, face_recognition, etc.)

        // Simulated face encoding generation
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(imageBytes);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private double compareFaceEncodings(byte[] stored, byte[] submitted) {
        // In production, this would compute actual face embedding similarity
        // Using cosine similarity, Euclidean distance, or model-specific comparison

        // Simulated comparison based on hash similarity
        if (stored.length != submitted.length) {
            return 0.0;
        }

        int matchingBytes = 0;
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] == submitted[i]) {
                matchingBytes++;
            }
        }

        // For simulation, return high similarity for enrolled faces
        // In production, actual face embedding comparison would be performed
        return 0.95; // Simulated high match for demonstration
    }

    private boolean performLivenessCheck(String livenessToken, byte[] imageBytes) {
        // In production, liveness detection would:
        // - Verify the challenge response
        // - Check for 3D depth information
        // - Analyze micro-movements
        // - Detect printed photos or screens
        // - Use IR sensors if available

        return livenessToken != null && !livenessToken.isEmpty();
    }

    public String generateLivenessChallenge(String voterId) {
        // Generate a challenge for liveness detection
        // Could be: blink, smile, turn head, etc.
        String challenge = UUID.randomUUID().toString();
        return Base64.getEncoder().encodeToString(challenge.getBytes());
    }

    public void cleanupExpiredTokens() {
        activeTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void resetAttemptCount(String voterId) {
        attemptCounts.remove(voterId);
    }

    private static class VerificationToken {
        private final String token;
        private final LocalDateTime expiry;

        public VerificationToken(String token, LocalDateTime expiry) {
            this.token = token;
            this.expiry = expiry;
        }

        public String getToken() {
            return token;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }
    }
}
