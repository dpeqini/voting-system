package danjel.blockchain.core;
import danjel.blockchain.utils.CryptoUtil;

import java.security.*;

public class Transaction {
    private String transactionId;
    private String voterCredentialHash;  // Anonymous credential
    private String candidateId;
    private String encryptedVote;
    private long timestamp;
    private byte[] signature;

    public Transaction(String voterCredentialHash, String candidateId) {
        this.voterCredentialHash = voterCredentialHash;
        this.candidateId = candidateId;
        this.timestamp = System.currentTimeMillis();
        this.transactionId = calculateTransactionId();
    }

    private String calculateTransactionId() {
        return CryptoUtil.applySha256(
                voterCredentialHash + candidateId + timestamp
        );
    }

    public void generateSignature(PrivateKey privateKey) {
        String data = voterCredentialHash + candidateId;
        this.signature = CryptoUtil.applySignature(privateKey, data);
    }

    public boolean verifySignature(PublicKey publicKey) {
        String data = voterCredentialHash + candidateId;
        return CryptoUtil.verifySignature(publicKey, data, signature);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getVoterCredentialHash() {
        return voterCredentialHash;
    }

    public void setVoterCredentialHash(String voterCredentialHash) {
        this.voterCredentialHash = voterCredentialHash;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getEncryptedVote() {
        return encryptedVote;
    }

    public void setEncryptedVote(String encryptedVote) {
        this.encryptedVote = encryptedVote;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }
}