package danjel.votingbackend.exception;

public class BlockchainException extends RuntimeException {
    public BlockchainException(String message) {
        super(message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}