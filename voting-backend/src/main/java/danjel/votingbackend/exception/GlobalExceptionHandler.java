package danjel.votingbackend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Auth & registration ───────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", ex.getMessage());
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationException(RegistrationException ex) {
        logger.warn("Registration failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Registration failed", ex.getMessage());
    }

    // ── Face verification ─────────────────────────────────────────────────────

    /**
     * Face images were rejected — bad image, no face detected, or face mismatch.
     * Returns 401 so the Android app knows to prompt the voter to try again.
     * Also covers rate-limit messages embedded in FaceVerificationException.
     */
    @ExceptionHandler(FaceVerificationException.class)
    public ResponseEntity<ErrorResponse> handleFaceVerificationException(FaceVerificationException ex) {
        String message = ex.getMessage();
        logger.warn("Face verification failed: {}", message);

        // Rate-limit messages are embedded in FaceVerificationException
        // with specific wording — return 429 for those
        if (message != null && message.contains("Too many failed face verification attempts")) {
            return buildErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    message);
        }

        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Face verification failed", message);
    }

    /**
     * Python DeepFace server is unreachable.
     * Returns 503 so the Android app can show "service unavailable, try later"
     * rather than a generic error — different UX from a face mismatch.
     */
    @ExceptionHandler(DeepFaceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDeepFaceUnavailable(DeepFaceUnavailableException ex) {
        logger.error("DeepFace server unavailable: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Face verification service unavailable",
                ex.getMessage());
    }

    // ── Domain exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(VotingException.class)
    public ResponseEntity<ErrorResponse> handleVotingException(VotingException ex) {
        logger.warn("Voting error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Voting error", ex.getMessage());
    }

    @ExceptionHandler(ElectionException.class)
    public ResponseEntity<ErrorResponse> handleElectionException(ElectionException ex) {
        logger.warn("Election error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Election error", ex.getMessage());
    }

    @ExceptionHandler(BlockchainException.class)
    public ResponseEntity<ErrorResponse> handleBlockchainException(BlockchainException ex) {
        logger.error("Blockchain error: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Blockchain error", ex.getMessage());
    }

    @ExceptionHandler(ExternalDataException.class)
    public ResponseEntity<ErrorResponse> handleExternalDataException(ExternalDataException ex) {
        logger.error("External data error: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY, "External service error", ex.getMessage());
    }

    // ── Spring / security ─────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "You don't have permission to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                "One or more fields are missing or invalid",
                LocalDateTime.now());
        response.setValidationErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later.");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(status.value(), error, message, LocalDateTime.now()));
    }

    // ── ErrorResponse ─────────────────────────────────────────────────────────

    public static class ErrorResponse {
        private int                 status;
        private String              error;
        private String              message;
        private LocalDateTime       timestamp;
        private Map<String, String> validationErrors;

        public ErrorResponse(int status, String error, String message, LocalDateTime timestamp) {
            this.status    = status;
            this.error     = error;
            this.message   = message;
            this.timestamp = timestamp;
        }

        public int                 getStatus()           { return status; }
        public String              getError()            { return error; }
        public String              getMessage()          { return message; }
        public LocalDateTime       getTimestamp()        { return timestamp; }
        public Map<String, String> getValidationErrors() { return validationErrors; }

        public void setStatus(int status)                               { this.status = status; }
        public void setError(String error)                              { this.error = error; }
        public void setMessage(String message)                          { this.message = message; }
        public void setTimestamp(LocalDateTime timestamp)               { this.timestamp = timestamp; }
        public void setValidationErrors(Map<String, String> errors)     { this.validationErrors = errors; }
    }
}