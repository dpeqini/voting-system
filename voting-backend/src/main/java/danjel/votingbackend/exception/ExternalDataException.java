package danjel.votingbackend.exception;

public class ExternalDataException extends RuntimeException {
    public ExternalDataException(String message) {
        super(message);
    }

    public ExternalDataException(String message, Throwable cause) {
        super(message, cause);
    }
}