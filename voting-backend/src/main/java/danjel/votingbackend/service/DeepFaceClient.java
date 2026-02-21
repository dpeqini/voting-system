package danjel.votingbackend.service;

import danjel.votingbackend.exception.DeepFaceUnavailableException;
import danjel.votingbackend.exception.FaceVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client that calls the Python DeepFace server for face comparison.
 *
 * The Android app sends two face images to the backend (chip photo + live selfie).
 * This client forwards them to the Python server internally — the images never
 * need to traverse the public internet as two separate hops.
 *
 * DeepFace /verify request body:
 * {
 *   "img1": "<base64 chip photo>",
 *   "img2": "<base64 live selfie>",
 *   "model_name": "Facenet512",
 *   "distance_metric": "cosine",
 *   "detector_backend": "opencv"
 * }
 *
 * DeepFace /verify response:
 * {
 *   "verified": true,
 *   "distance": 0.21,
 *   "threshold": 0.40,
 *   "model": "Facenet512",
 *   "time": 1.23
 * }
 */
@Component
public class DeepFaceClient {

    private static final Logger logger = LoggerFactory.getLogger(DeepFaceClient.class);

    private final RestTemplate restTemplate;

    @Value("${deepface.server.url:http://localhost:5005}")
    private String serverUrl;

    @Value("${deepface.model:Facenet512}")
    private String model;

    @Value("${deepface.distance-metric:cosine}")
    private String distanceMetric;

    @Value("${deepface.detector-backend:opencv}")
    private String detectorBackend;

    @Value("${deepface.max-distance:0.40}")
    private double maxDistance;

    @Value("${deepface.timeout-ms:15000}")
    private int timeoutMs;

    public DeepFaceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Compares two base64-encoded face images using the DeepFace Python server.
     *
     * @param chipFaceBase64  Face photo extracted from NFC chip (base64)
     * @param liveFaceBase64  Live selfie captured by Android camera (base64)
     * @return DeepFaceResult with verified flag, distance, and threshold
     * @throws DeepFaceUnavailableException if Python server is unreachable
     * @throws FaceVerificationException    if server returns an error response
     */
    public DeepFaceResult verify(String chipFaceBase64, String liveFaceBase64) {
        validateImageNotBlank(chipFaceBase64, "Chip face image");
        validateImageNotBlank(liveFaceBase64, "Live face image");

        String url = serverUrl + "/verify";

        Map<String, Object> requestBody = Map.of(
                "img1",             chipFaceBase64,
                "img2",             liveFaceBase64,
                "model_name",       model,
                "distance_metric",  distanceMetric,
                "detector_backend", detectorBackend
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            logger.debug("Calling DeepFace /verify  model={}  metric={}", model, distanceMetric);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new FaceVerificationException(
                        "DeepFace server returned unexpected status: " + response.getStatusCode());
            }

            return parseResponse(response.getBody());

        } catch (ResourceAccessException e) {
            // Python server is not running or unreachable
            logger.error("DeepFace server unreachable at {}  error={}", url, e.getMessage());
            throw new DeepFaceUnavailableException(
                    "Face verification service is temporarily unavailable. Please try again later.");
        } catch (FaceVerificationException | DeepFaceUnavailableException e) {
            throw e;  // re-throw our own exceptions as-is
        } catch (Exception e) {
            logger.error("Unexpected error calling DeepFace server", e);
            throw new FaceVerificationException(
                    "Face verification failed due to an unexpected error: " + e.getMessage());
        }
    }

    /**
     * Checks if the Python server is running and healthy.
     * Used at startup and can be exposed via an admin health endpoint.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<Map> response =
                    restTemplate.getForEntity(serverUrl + "/health", Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("DeepFace health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private DeepFaceResult parseResponse(Map<?, ?> body) {
        // Handle DeepFace error responses (it returns 200 with {"error": "..."} in some cases)
        if (body.containsKey("error")) {
            String error = String.valueOf(body.get("error"));

            if (error.contains("Face could not be detected") || error.contains("No face")) {
                throw new FaceVerificationException(
                        "No face detected in one or both images. " +
                                "Ensure the chip photo is clear and your selfie shows your full face.");
            }
            if (error.contains("multiple faces") || error.contains("Multiple faces")) {
                throw new FaceVerificationException(
                        "Multiple faces detected. Please ensure only your face is visible.");
            }

            throw new FaceVerificationException("Face verification error: " + error);
        }

        Boolean verified     = (Boolean) body.get("verified");
        Object  distanceObj  = body.get("distance");
        Object  thresholdObj = body.get("threshold");
        Object  modelObj     = body.get("model");
        String  returnedModel = (modelObj != null) ? String.valueOf(modelObj) : model;

        if (verified == null || distanceObj == null || thresholdObj == null) {
            throw new FaceVerificationException(
                    "DeepFace server returned an incomplete response. Missing required fields.");
        }

        double distance  = ((Number) distanceObj).doubleValue();
        double threshold = ((Number) thresholdObj).doubleValue();

        // Extra server-side gate: even if DeepFace says verified, reject if distance
        // exceeds our configured maximum (allows us to tighten threshold beyond DeepFace default)
        if (verified && distance > maxDistance) {
            logger.warn("DeepFace verified=true but distance={} exceeds maxDistance={}. Rejecting.",
                    distance, maxDistance);
            verified = false;
        }

        logger.info("DeepFace result  verified={}  distance={}  threshold={}  model={}",
                verified, distance, threshold, returnedModel);

        return new DeepFaceResult(verified, distance, threshold, returnedModel);
    }

    private void validateImageNotBlank(String base64, String fieldName) {
        if (base64 == null || base64.isBlank()) {
            throw new FaceVerificationException(fieldName + " is required for face verification.");
        }
        // Rough base64 sanity check — a face image should be at least ~5KB
        if (base64.length() < 5000) {
            throw new FaceVerificationException(
                    fieldName + " appears too small. Please send a full-resolution image.");
        }
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record DeepFaceResult(
            boolean verified,
            double distance,
            double threshold,
            String model
    ) {}
}