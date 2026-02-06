package danjel.votingbackend.service;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.exception.VotingException;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Vote;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.CandidateRepository;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.VoteRepository;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class VotingService {

    private final VoteRepository voteRepository;
    private final VoterRepository voterRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final BlockchainService blockchainService;
    private final FaceRecognitionService faceRecognitionService;

    public VotingService(VoteRepository voteRepository,
                         VoterRepository voterRepository,
                         ElectionRepository electionRepository,
                         CandidateRepository candidateRepository,
                         BlockchainService blockchainService,
                         FaceRecognitionService faceRecognitionService) {
        this.voteRepository = voteRepository;
        this.voterRepository = voterRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.blockchainService = blockchainService;
        this.faceRecognitionService = faceRecognitionService;
    }

    @Transactional
    public VoteResponse castVote(String voterId, VoteRequest request) {
        // Get voter
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> {
                    return new VotingException("Voter not found");
                });

        // Validate voter eligibility
        validateVoterEligibility(voter);

        // Get election
        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new VotingException("Election not found"));

        // Validate election status
        validateElectionStatus(election);

        // Check if voter already voted in this election
        if (voter.hasVotedIn(election.getId())) {
            throw new VotingException("You have already voted in this election");
        }

        // Validate face verification token if required
        if (request.getFaceVerificationToken() != null) {
            if (!faceRecognitionService.validateVerificationToken(voterId, request.getFaceVerificationToken())) {
                throw new VotingException("Face verification failed or token expired");
            }
        }

        // Validate candidate belongs to voter's region
        validateCandidateForVoter(voter, election, request.getCandidateId());

        // Generate voter hash (anonymous identifier)
        String voterHash = generateVoterHash(voterId, election.getId());

        // Check for duplicate vote using hash
        if (voteRepository.existsByVoterHashAndElectionId(voterHash, election.getId())) {
            throw new VotingException("Duplicate vote detected");
        }

        // Generate vote hash
        String voteHash = generateVoteHash(request, voterHash);

        // Create vote record
        Vote vote = new Vote(election, voterHash, request.getEncryptedVoteData(), voteHash);
        vote.setCandidateId(request.getCandidateId());
        vote.setPartyId(request.getPartyId());
        vote.setCounty(voter.getCounty());
        vote.setMunicipality(voter.getMunicipality());
        vote.setVotingStationId(request.getVotingStationId());
        vote.setDigitalSignature(request.getDigitalSignature());
        vote.setNonce(request.getNonce() != null ? request.getNonce() : UUID.randomUUID().toString());

        // Save vote
        vote = voteRepository.save(vote);

        // Add to blockchain
        String transactionId = blockchainService.addVoteToBlockchain(vote);
        vote.setBlockchainTransactionId(transactionId);
        voteRepository.save(vote);

        // Mark voter as having voted
        voter.markAsVoted(election.getId());
        voterRepository.save(voter);

        // Update election vote count
        election.incrementVoteCount();
        electionRepository.save(election);

        // Generate response
        VoteResponse response = VoteResponse.success(vote.getId(), voteHash, transactionId);
        response.setBlockNumber(vote.getBlockNumber());
        response.setVerificationCode(generateVerificationCode(voteHash));
        response.setReceiptToken(generateReceiptToken(vote));

        return response;
    }

    private void validateVoterEligibility(Voter voter) {
        if (!voter.isVerified()) {
            throw new VotingException("Voter account is not verified");
        }
        if (!voter.isEnabled()) {
            throw new VotingException("Voter account is disabled");
        }
        if (voter.isAccountLocked()) {
            throw new VotingException("Voter account is locked");
        }
        if (!voter.isEligibleToVote()) {
            throw new VotingException("Voter is not eligible to vote");
        }
    }

    private void validateElectionStatus(Election election) {
        if (election.getStatus() != ElectionStatus.STARTED) {
            throw new VotingException("Election is not currently active");
        }
        if (!election.canVote()) {
            throw new VotingException("Voting period has ended or not started");
        }
    }

    private void validateCandidateForVoter(Voter voter, Election election, String candidateId) {
        if (candidateId == null) return;

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new VotingException("Candidate not found"));

        if (!candidate.getElection().getId().equals(election.getId())) {
            throw new VotingException("Candidate does not belong to this election");
        }

        if (election.getElectionType() == ElectionType.PARLIAMENTARY) {
            if (candidate.getCounty() != voter.getCounty()) {
                throw new VotingException("Candidate is not from your county");
            }
        } else if (election.getElectionType() == ElectionType.LOCAL_GOVERNMENT) {
            if (candidate.getMunicipality() != voter.getMunicipality()) {
                throw new VotingException("Candidate is not from your municipality");
            }
        }
    }

    private String generateVoterHash(String voterId, String electionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = voterId + "|" + electionId + "|" + UUID.randomUUID();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VotingException("Hash generation failed");
        }
    }

    private String generateVoteHash(VoteRequest request, String voterHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = voterHash + "|" + request.getElectionId() + "|" +
                    request.getCandidateId() + "|" + request.getEncryptedVoteData() + "|" +
                    LocalDateTime.now().toString();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VotingException("Vote hash generation failed");
        }
    }

    private String generateVerificationCode(String voteHash) {
        return voteHash.substring(0, 8).toUpperCase();
    }

    private String generateReceiptToken(Vote vote) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = vote.getId() + "|" + vote.getVoteHash() + "|" + vote.getTimestamp();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return vote.getVoteHash().substring(0, 32);
        }
    }

    public List<Candidate> getCandidatesForVoter(String voterId, String electionId) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new VotingException("Voter not found"));

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VotingException("Election not found"));

        if (election.getElectionType() == ElectionType.PARLIAMENTARY) {
            return candidateRepository.findByElectionAndCounty(electionId, voter.getCounty());
        } else {
            return candidateRepository.findByElectionAndMunicipality(electionId, voter.getMunicipality());
        }
    }

    public boolean hasVoted(String voterId, String electionId) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new VotingException("Voter not found"));
        return voter.hasVotedIn(electionId);
    }
}