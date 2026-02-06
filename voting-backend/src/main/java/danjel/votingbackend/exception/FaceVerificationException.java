package danjel.votingbackend.exception;

public class FaceVerificationException extends RuntimeException {
    public FaceVerificationException(String message) {
        super(message);
    }

    public FaceVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
