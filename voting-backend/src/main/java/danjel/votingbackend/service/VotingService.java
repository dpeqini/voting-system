package danjel.votingbackend.service;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.exception.VotingException;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Party;
import danjel.votingbackend.model.Vote;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.CandidateRepository;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.PartyRepository;
import danjel.votingbackend.repository.VoteRepository;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VotingService {
    @Value("${voting.secret.salt}")
    private String secretSalt;
    private final VoteRepository      voteRepository;
    private final VoterRepository     voterRepository;
    private final ElectionRepository  electionRepository;
    private final CandidateRepository candidateRepository;
    private final PartyRepository     partyRepository;
    private final BlockchainService   blockchainService;

    public VotingService(VoteRepository voteRepository,
                         VoterRepository voterRepository,
                         ElectionRepository electionRepository,
                         CandidateRepository candidateRepository,
                         PartyRepository partyRepository,
                         BlockchainService blockchainService) {
        this.voteRepository      = voteRepository;
        this.voterRepository     = voterRepository;
        this.electionRepository  = electionRepository;
        this.candidateRepository = candidateRepository;
        this.partyRepository     = partyRepository;
        this.blockchainService   = blockchainService;
    }

    @Transactional
    public VoteResponse castVote(String voterId, VoteRequest request) {

        // ── 1. Load voter ──────────────────────────────────────────────────────
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new VotingException("Voter not found"));

        // ── 2. Eligibility ─────────────────────────────────────────────────────
        // The JWT reaching this endpoint already proves the voter passed:
        //   - NFC chip read + chip signature verification (on-device)
        //   - ML Kit liveness detection (on-device)
        //   - DeepFace face comparison (backend, via IdCardAuthService)
        // So here we only check account-level flags and card validity.
        validateVoterEligibility(voter);

        //  Verify the cryptographic RSA signature before accepting the vote
        verifyVoterSignature(request, voter);
        // ── 3. Load & validate election ────────────────────────────────────────
        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new VotingException("Election not found"));
        validateElectionStatus(election);

        // ── 4. Prevent double voting ───────────────────────────────────────────
        if (voter.hasVotedIn(election.getId())) {
            throw new VotingException("You have already voted in this election");
        }
        String voterHash = generateVoterHash(voterId, election.getId());
        if (voteRepository.existsByVoterHashAndElectionId(voterHash, election.getId())) {
            throw new VotingException("Duplicate vote detected");
        }

        // ── 5. Resolve Candidate FK ────────────────────────────────────────────
        Candidate candidate = null;
        if (request.getCandidateId() != null && !request.getCandidateId().isBlank()) {
            candidate = candidateRepository.findById(request.getCandidateId())
                    .orElseThrow(() -> new VotingException("Candidate not found"));
            validateCandidateForVoter(voter, election, candidate);
        }

        // ── 6. Resolve Party FK ────────────────────────────────────────────────
        Party party = null;
        if (request.getPartyId() != null && !request.getPartyId().isBlank()) {
            party = partyRepository.findById(request.getPartyId())
                    .orElseThrow(() -> new VotingException("Party not found"));
            if (!party.getElection().getId().equals(election.getId())) {
                throw new VotingException("Party does not belong to this election");
            }
        }

        if (candidate == null && party == null) {
            throw new VotingException("A candidate or party must be selected");
        }

        // ── 7. Build & persist Vote ────────────────────────────────────────────
        String voteHash = generateVoteHash(request, voterHash);
        Vote vote = new Vote(election, voterHash, request.getEncryptedVoteData(), voteHash);
        vote.setCandidate(candidate);
        vote.setParty(party);
        vote.setCounty(voter.getCounty());
        vote.setMunicipality(voter.getMunicipality());
        vote.setVotingStationId(request.getVotingStationId());
        vote.setDigitalSignature(request.getDigitalSignature());
        vote.setNonce(request.getNonce() != null ? request.getNonce() : UUID.randomUUID().toString());

        Vote saved = voteRepository.save(vote);

        // Persist the receiptToken so GET /verification/vote/receipt/{token} can resolve it later
        String receiptToken = generateReceiptToken(saved);
        saved.setReceiptToken(receiptToken);
        saved = voteRepository.save(saved);

        blockchainService.addVoteToBlockchain(saved);

        // ── 8. Mark voter as having voted ──────────────────────────────────────
        voter.recordVote(election.getId());
        election.setTotalVotesCast(election.getTotalVotesCast() + 1);
        voterRepository.save(voter);
        electionRepository.save(election);

        return buildVoteResponse(saved);
    }


    // ── Validation ─────────────────────────────────────────────────────────────

    private void validateVoterEligibility(Voter voter) {
        if (!voter.isEnabled()) {
            throw new VotingException(
                    "Your voter account has been disabled. Please contact the election commission.");
        }
        // Re-check age and card validity using the stored chip data
        if (!voter.isAgeEligible()) {
            throw new VotingException("You must be at least 18 years old to vote.");
        }
        if (!voter.isCardValid()) {
            throw new VotingException(
                    "Your ID card has expired. Please renew it and re-authenticate.");
        }
    }

    private void validateElectionStatus(Election election) {
        if (election.getStatus() != ElectionStatus.STARTED) {
            throw new VotingException("Election is not currently active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (election.getStartDate() != null && now.isBefore(election.getStartDate())) {
            throw new VotingException("Election has not started yet");
        }
        if (election.getEndDate() != null && now.isAfter(election.getEndDate())) {
            throw new VotingException("Election has ended");
        }
    }

    private void validateCandidateForVoter(Voter voter, Election election, Candidate candidate) {
        if (!candidate.getElection().getId().equals(election.getId())) {
            throw new VotingException("Candidate does not belong to this election");
        }
        if (!candidate.isActive()) {
            throw new VotingException("Candidate is no longer active");
        }
        if (election.getElectionType() == ElectionType.PARLIAMENTARY) {
            if (candidate.getCounty() != null && !candidate.getCounty().equals(voter.getCounty())) {
                throw new VotingException("Candidate is not in your county");
            }
        } else if (election.getElectionType() == ElectionType.LOCAL_GOVERNMENT) {
            if (candidate.getMunicipality() != null
                    && !candidate.getMunicipality().equals(voter.getMunicipality())) {
                throw new VotingException("Candidate is not in your municipality");
            }
        }
    }
    private void verifyVoterSignature(VoteRequest request, Voter voter) {
        if (voter.getPublicKey() == null || voter.getPublicKey().isBlank()) {
            throw new VotingException("No public key registered for this device. Please re-authenticate.");
        }

        try {
            // A. Reconstruct the exact string the phone signed
            String candidateId = (request.getCandidateId() != null && !request.getCandidateId().isBlank())
                    ? request.getCandidateId() : "NONE";
            String partyId = (request.getPartyId() != null && !request.getPartyId().isBlank())
                    ? request.getPartyId() : "NONE";

            String payloadToSign = request.getElectionId() + ":" + candidateId + ":" + partyId;

            // B. Decode the voter's registered Public Key
            byte[] keyBytes = Base64.getDecoder().decode(voter.getPublicKey());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            // C. Verify the signature
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(payloadToSign.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(request.getDigitalSignature());
            boolean isValid = sig.verify(signatureBytes);

            if (!isValid) {
                throw new VotingException("Cryptographic signature verification failed. Vote payload was tampered with.");
            }
        } catch (VotingException e) {
            throw e;
        } catch (Exception e) {
            throw new VotingException("Invalid signature format or keys: " + e.getMessage());
        }
    }
    // ── Candidates ──────────────────────────────────────────────────────────────

    public List<CandidateResponse> getCandidatesForVoter(String voterId, String electionId) {
        Voter voter = voterRepository.findById(voterId)
                .orElseThrow(() -> new VotingException("Voter not found"));
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VotingException("Election not found"));

        List<Candidate> candidates;
        if (election.getElectionType() == ElectionType.PARLIAMENTARY) {
            candidates = candidateRepository.findByElectionIdAndCountyWithParty(electionId, voter.getCounty());
        } else {
            candidates = candidateRepository.findByElectionIdAndMunicipalityWithParty(electionId, voter.getMunicipality());
        }
        return candidates.stream().map(this::mapCandidateToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasVoted(String voterId, String electionId) {
        return voterRepository.findById(voterId)
                .map(v -> v.hasVotedIn(electionId))
                .orElse(false);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String generateVoterHash(String voterId, String electionId) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest((voterId + ":" + electionId + ":" + secretSalt)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VotingException("Failed to generate voter hash");
        }
    }
    private String generateVoteHash(VoteRequest request, String voterHash) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest((voterHash + ":" + request.getElectionId()
                    + ":" + request.getEncryptedVoteData()
                    + ":" + System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VotingException("Failed to generate vote hash");
        }
    }

    private String generateVerificationCode(String voteHash) {
        return voteHash.substring(0, Math.min(8, voteHash.length())).toUpperCase();
    }

    private String generateReceiptToken(Vote vote) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest((vote.getId() + ":" + vote.getVoteHash()
                    + ":" + vote.getTimestamp()).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    private VoteResponse buildVoteResponse(Vote vote) {
        VoteResponse r = VoteResponse.success(
                vote.getId(), vote.getVoteHash(), vote.getBlockchainTransactionId());
        r.setBlockNumber(vote.getBlockNumber());
        r.setTimestamp(vote.getTimestamp());
        r.setCounty(vote.getCounty());
        r.setMunicipality(vote.getMunicipality());
        r.setVerificationCode(generateVerificationCode(vote.getVoteHash()));
        r.setReceiptToken(vote.getReceiptToken());   // already persisted — don't regenerate
        r.setElectionName(vote.getElection().getName());
        if (vote.getCandidate() != null) r.setCandidateName(vote.getCandidate().getFullName());
        if (vote.getParty()     != null) r.setPartyName(vote.getParty().getName());
        return r;
    }

    private CandidateResponse mapCandidateToResponse(Candidate c) {
        CandidateResponse r = new CandidateResponse();
        r.setId(c.getId());
        r.setFirstName(c.getFirstName());
        r.setLastName(c.getLastName());
        r.setFullName(c.getFullName());
        r.setBiography(c.getBiography());
        r.setPhotoUrl(c.getPhotoUrl());
        r.setCounty(c.getCounty());
        r.setCountyName(c.getCounty() != null ? c.getCounty().getDisplayName() : null);
        r.setMunicipality(c.getMunicipality());
        r.setMunicipalityName(c.getMunicipality() != null ? c.getMunicipality().getDisplayName() : null);
        r.setPositionInList(c.getPositionInList());
        r.setIndependent(c.isIndependent());
        r.setProfession(c.getProfession());
        r.setAge(c.getAge());
        r.setEducation(c.getEducation());
        r.setPlatform(c.getPlatform());
        if (c.getParty() != null) {
            r.setPartyId(c.getParty().getId());
            r.setPartyName(c.getParty().getName());
            r.setPartyCode(c.getParty().getPartyCode());
        }
        return r;
    }
}