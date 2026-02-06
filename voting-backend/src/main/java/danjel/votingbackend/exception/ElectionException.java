package danjel.votingbackend.exception;

public class ElectionException extends RuntimeException {
    public ElectionException(String message) {
        super(message);
    }

    public ElectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
