package danjel.blockchain.core;

import java.time.LocalDateTime;
import java.util.*;

public class SmartContract {
    private String electionId;
    private String electionName;
    private List<Candidate> candidates;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean isActive;
    private Set<String> registeredVoterHashes;

    public SmartContract(String electionId, String name,
                               LocalDateTime start, LocalDateTime end) {
        this.electionId = electionId;
        this.electionName = name;
        this.startTime = start;
        this.endTime = end;
        this.candidates = new ArrayList<>();
        this.registeredVoterHashes = new HashSet<>();
        this.isActive = false;
    }

    public void addCandidate(String id, String name, String party) {
        candidates.add(new Candidate(id, name, party));
    }

    public void registerVoter(String voterCredentialHash) {
        registeredVoterHashes.add(voterCredentialHash);
    }

    public boolean validateVote(String voterCredentialHash, String candidateId) {
        // Check if election is active
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            return false;
        }

        // Check if voter is registered
        if (!registeredVoterHashes.contains(voterCredentialHash)) {
            return false;
        }

        // Check if candidate exists
        return candidates.stream()
                .anyMatch(c -> c.getId().equals(candidateId));
    }

    public void startElection() {
        this.isActive = true;
    }

    public void endElection() {
        this.isActive = false;
    }

    // Inner class for Candidate
    public static class Candidate {
        private String id;
        private String name;
        private String party;

        public Candidate(String id, String name, String party) {
            this.id = id;
            this.name = name;
            this.party = party;
        }

        public String getId() {
            return id;
        }
    }
}
