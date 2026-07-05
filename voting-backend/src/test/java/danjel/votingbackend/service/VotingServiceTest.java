package danjel.votingbackend.service;

import danjel.votingbackend.dto.VoteRequest;
import danjel.votingbackend.dto.VoteResponse;
import danjel.votingbackend.exception.VotingException;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Unit tests for the vote-casting pipeline (VotingService), Kreu V/VI.
 *
 * Repositories and the blockchain service are mocked; a real RSA key pair is used
 * so the device-signature verification (verifyVoterSignature) runs against genuine
 * cryptography. Covers: eligibility gates, RSA signature verification, the two
 * independent double-vote barriers, and voteHash / receiptToken generation.
 */
class VotingServiceTest {

    private static KeyPair voterKeyPair;

    private VoteRepository      voteRepository;
    private VoterRepository     voterRepository;
    private ElectionRepository  electionRepository;
    private CandidateRepository candidateRepository;
    private PartyRepository     partyRepository;
    private BlockchainService   blockchainService;
    private VotingService       service;

    private Election election;
    private Voter    voter;
    private Party    party;

    private static final String VOTER_ID    = "voter-1";
    private static final String ELECTION_ID = "election-1";
    private static final String PARTY_ID    = "party-1";

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        voterKeyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        voteRepository      = mock(VoteRepository.class);
        voterRepository     = mock(VoterRepository.class);
        electionRepository  = mock(ElectionRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        partyRepository     = mock(PartyRepository.class);
        blockchainService   = mock(BlockchainService.class);

        service = new VotingService(voteRepository, voterRepository, electionRepository,
                candidateRepository, partyRepository, blockchainService);
        setField(service, "secretSalt", "unit-test-salt");

        election = new Election();
        election.setId(ELECTION_ID);
        election.setName("Zgjedhjet Parlamentare 2025");
        election.setStatus(ElectionStatus.STARTED);
        election.setElectionType(ElectionType.PARLIAMENTARY);

        voter = new Voter();
        voter.setId(VOTER_ID);
        voter.setEnabled(true);
        voter.setDateOfBirth(LocalDate.now().minusYears(30));
        voter.setCardExpiryDate(LocalDate.now().plusYears(3));
        voter.setPublicKey(Base64.getEncoder()
                .encodeToString(voterKeyPair.getPublic().getEncoded()));

        party = new Party("PS", "Partia Socialiste");
        party.setId(PARTY_ID);
        party.setElection(election);

        // Common happy-path stubs (manual mocks are lenient — unused stubs are fine).
        when(voterRepository.findById(VOTER_ID)).thenReturn(Optional.of(voter));
        when(electionRepository.findById(ELECTION_ID)).thenReturn(Optional.of(election));
        when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(party));
        when(voteRepository.existsByVoterHashAndElection_Id(anyString(), eq(ELECTION_ID)))
                .thenReturn(false);
        when(voteRepository.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Signature helpers ────────────────────────────────────────────────────────

    private String sign(String payload) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(voterKeyPair.getPrivate());
        s.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    /** A party-only ballot request with a correct device signature. */
    private VoteRequest validSignedRequest() throws Exception {
        VoteRequest req = new VoteRequest();
        req.setElectionId(ELECTION_ID);
        req.setPartyId(PARTY_ID);
        req.setEncryptedVoteData("AES-ENCRYPTED-BALLOT");
        req.setNonce("nonce-1");
        // Payload the phone signs: electionId:candidateId(or NONE):partyId
        req.setDigitalSignature(sign(ELECTION_ID + ":NONE:" + PARTY_ID));
        return req;
    }

    // ── Happy path ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A valid, signed ballot is accepted and returns a receipt")
    void castVoteHappyPath() throws Exception {
        VoteResponse response = service.castVote(VOTER_ID, validSignedRequest());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getVoteHash()).isNotBlank();
        assertThat(response.getReceiptToken()).isNotBlank();
        assertThat(response.getVerificationCode()).hasSizeLessThanOrEqualTo(8);
        assertThat(response.getPartyName()).isEqualTo("Partia Socialiste");

        // Saved once to obtain an id, again to persist the receipt token.
        verify(voteRepository, times(2)).save(any(Vote.class));
        verify(blockchainService, times(1)).addVoteToBlockchain(any(Vote.class));
        assertThat(voter.hasVotedIn(ELECTION_ID)).isTrue();
    }

    // ── Double-vote prevention ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Double vote — first barrier: voter already recorded for this election")
    void castVoteRejectedWhenVoterAlreadyVoted() throws Exception {
        voter.recordVote(ELECTION_ID);

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("already voted");

        verify(blockchainService, never()).addVoteToBlockchain(any());
    }

    @Test
    @DisplayName("Double vote — second barrier: a vote with the same voterHash exists")
    void castVoteRejectedWhenVoterHashExists() throws Exception {
        when(voteRepository.existsByVoterHashAndElection_Id(anyString(), eq(ELECTION_ID)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("Duplicate vote");

        verify(voteRepository, never()).save(any());
    }

    // ── Signature / device binding ─────────────────────────────────────────────────

    @Test
    @DisplayName("A tampered payload (signature over different data) is rejected")
    void castVoteRejectsTamperedSignature() throws Exception {
        VoteRequest req = new VoteRequest();
        req.setElectionId(ELECTION_ID);
        req.setPartyId(PARTY_ID);
        req.setEncryptedVoteData("AES-ENCRYPTED-BALLOT");
        // Signature covers a DIFFERENT party than the one in the request.
        req.setDigitalSignature(sign(ELECTION_ID + ":NONE:party-999"));

        assertThatThrownBy(() -> service.castVote(VOTER_ID, req))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("A voter with no registered public key cannot vote")
    void castVoteRejectsMissingPublicKey() throws Exception {
        voter.setPublicKey(null);

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("public key");
    }

    // ── Eligibility gates ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("An unknown voter id is rejected")
    void castVoteRejectsUnknownVoter() {
        when(voterRepository.findById("ghost")).thenReturn(Optional.empty());

        VoteRequest req = new VoteRequest();
        req.setElectionId(ELECTION_ID);

        assertThatThrownBy(() -> service.castVote("ghost", req))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("Voter not found");
    }

    @Test
    @DisplayName("A disabled voter account is rejected")
    void castVoteRejectsDisabledVoter() throws Exception {
        voter.setEnabled(false);

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("An underage voter is rejected")
    void castVoteRejectsUnderageVoter() throws Exception {
        voter.setDateOfBirth(LocalDate.now().minusYears(16));

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("18");
    }

    @Test
    @DisplayName("An expired ID card is rejected")
    void castVoteRejectsExpiredCard() throws Exception {
        voter.setCardExpiryDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.castVote(VOTER_ID, validSignedRequest()))
                .isInstanceOf(VotingException.class)
                .hasMessageContaining("expired");
    }

    // ── hasVoted query ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasVoted reflects the voter's recorded elections")
    void hasVotedReflectsState() {
        assertThat(service.hasVoted(VOTER_ID, ELECTION_ID)).isFalse();

        voter.recordVote(ELECTION_ID);
        assertThat(service.hasVoted(VOTER_ID, ELECTION_ID)).isTrue();
        assertThat(service.hasVoted(VOTER_ID, "other-election")).isFalse();
    }
}
