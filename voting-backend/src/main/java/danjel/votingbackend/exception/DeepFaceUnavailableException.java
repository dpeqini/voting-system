package danjel.votingbackend.exception;

/**
 * Thrown when the DeepFace Python server cannot be reached.
 * Distinct from FaceVerificationException (which means the server responded
 * but rejected the images) so the GlobalExceptionHandler can return a
 * 503 Service Unavailable instead of a 400.
 */
public class DeepFaceUnavailableException extends RuntimeException {

    public DeepFaceUnavailableException(String message) {
        super(message);
    }

    public DeepFaceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}