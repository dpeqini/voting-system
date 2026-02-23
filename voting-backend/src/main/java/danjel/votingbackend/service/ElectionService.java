package danjel.votingbackend.service;

import danjel.votingbackend.dto.election.CandidateResponse;
import danjel.votingbackend.dto.election.ElectionRequest;
import danjel.votingbackend.dto.election.ElectionResponse;
import danjel.votingbackend.dto.election.PartyResponse;
import danjel.votingbackend.exception.ElectionException;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Party;
import danjel.votingbackend.repository.CandidateRepository;
import danjel.votingbackend.repository.ElectionRepository;
import danjel.votingbackend.repository.PartyRepository;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ElectionService {

    private static final Logger logger = LoggerFactory.getLogger(ElectionService.class);

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final PartyRepository partyRepository;
    private final VoterRepository voterRepository;
    private final BlockchainService blockchainService;
    private final ExternalDataService externalDataService;

    public ElectionService(ElectionRepository electionRepository,
                           CandidateRepository candidateRepository,
                           PartyRepository partyRepository,
                           VoterRepository voterRepository,
                           BlockchainService blockchainService,
                           ExternalDataService externalDataService) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.partyRepository = partyRepository;
        this.voterRepository = voterRepository;
        this.blockchainService = blockchainService;
        this.externalDataService = externalDataService;
    }

    @Transactional
    public ElectionResponse createElection(ElectionRequest request) {
        Election election = new Election(
                request.getName(),
                request.getElectionType(),
                request.getElectionDate(),
                request.getRegistrationDeadline()
        );

        election.setDescription(request.getDescription());
        election.setStartDate(request.getStartDate());
        election.setEndDate(request.getEndDate());
        election.setExternalDataSource(request.getExternalDataSource());
        election.setStatus(ElectionStatus.CREATED);

        // Calculate eligible voters based on election type
        long eligibleVoters = calculateEligibleVoters(request.getElectionType());
        election.setTotalEligibleVoters(eligibleVoters);

        election = electionRepository.save(election);

        logger.info("Election created: {} ({})", election.getName(), election.getId());

        return mapToResponse(election);
    }

    @Transactional
    public ElectionResponse importCandidates(String electionId, String dataSourceUrl) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> {
                    return new ElectionException("Election not found");
                });

        if (!election.canImportCandidates()) {
            throw new ElectionException("Cannot import candidates in current election status: " + election.getStatus());
        }

        // Clear existing candidates if re-importing
        if (election.isCandidatesImported()) {
            candidateRepository.deleteByElectionId(electionId);
            partyRepository.deleteByElectionId(electionId);
        }

        ExternalDataService.ImportResult result = externalDataService.importPartiesAndCandidates(
                election,
                dataSourceUrl != null ? dataSourceUrl : election.getExternalDataSource()
        );

        if (!result.isSuccess()) {
            throw new ElectionException("Failed to import candidates: " + result.getMessage());
        }

        election.setCandidatesImported(true);
        election.setStatus(ElectionStatus.CANDIDATES_IMPORTED);
        election.setLastSyncedAt(LocalDateTime.now());
        electionRepository.save(election);

        logger.info("Candidates imported for election {}: {} parties, {} candidates",
                electionId, result.getPartiesImported(), result.getCandidatesImported());

        return mapToResponse(election);
    }

    @Transactional
    public ElectionResponse startElection(String electionId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionException("Election not found"));

        if (election.getStatus() != ElectionStatus.CANDIDATES_IMPORTED) {
            throw new ElectionException("Election must have candidates imported before starting");
        }

        if (!election.isCandidatesImported()) {
            throw new ElectionException("No candidates imported for this election");
        }

        // Initialize blockchain for this election
        String genesisHash = blockchainService.initializeBlockchain(election);

        election.setStatus(ElectionStatus.STARTED);
        election.setStartDate(LocalDateTime.now());
        election.setGenesisBlockHash(genesisHash);

        electionRepository.save(election);

        logger.info("Election started: {} with genesis hash: {}", election.getName(), genesisHash);

        return mapToResponse(election);
    }

    @Transactional
    public ElectionResponse closeElection(String electionId, String closedBy) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionException("Election not found"));

        if (election.getStatus() != ElectionStatus.STARTED) {
            throw new ElectionException("Only started elections can be closed");
        }

        // Flush any pending votes to blockchain
        blockchainService.flushPendingVotes(electionId);

        election.setStatus(ElectionStatus.CLOSED);
        election.setEndDate(LocalDateTime.now());
        election.setResultsPublishedBy(closedBy);
        election.setResultsPublishedAt(LocalDateTime.now());

        electionRepository.save(election);

        logger.info("Election closed: {} by {}", election.getName(), closedBy);

        return mapToResponse(election);
    }

    public ElectionResponse getElection(String electionId) {
        Election election = electionRepository.findByIdWithCandidatesAndParties(electionId)
                .orElseThrow(() -> new ElectionException("Election not found"));
        return mapToResponse(election);
    }

    public List<ElectionResponse> getActiveElections() {
        return electionRepository.findActiveElections(LocalDateTime.now())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Page<ElectionResponse> getAllElections(Pageable pageable) {
        return electionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    public List<ElectionResponse> getElectionsByStatus(ElectionStatus status) {
        return electionRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CandidateResponse> getCandidatesForElection(String electionId) {
        return candidateRepository.findByElectionIdAndActive(electionId, true)
                .stream()
                .map(this::mapCandidateToResponse)
                .collect(Collectors.toList());
    }

    public List<CandidateResponse> getCandidatesForVoterRegion(String electionId,
                                                               AlbanianCounty county,
                                                               AlbanianMunicipality municipality) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionException("Election not found"));

        List<Candidate> candidates;
        if (election.getElectionType() == ElectionType.PARLIAMENTARY) {
            candidates = candidateRepository.findByElectionAndCounty(electionId, county);
        } else {
            candidates = candidateRepository.findByElectionAndMunicipality(electionId, municipality);
        }

        return candidates.stream()
                .map(this::mapCandidateToResponse)
                .collect(Collectors.toList());
    }

    public List<PartyResponse> getPartiesForElection(String electionId) {
        return partyRepository.findPartiesWithCandidates(electionId)
                .stream()
                .map(this::mapPartyToResponse)
                .collect(Collectors.toList());
    }

    private long calculateEligibleVoters(ElectionType type) {
        return voterRepository.countEligibleVoters();
    }

    private ElectionResponse mapToResponse(Election election) {
        ElectionResponse response = new ElectionResponse();
        response.setId(election.getId());
        response.setName(election.getName());
        response.setDescription(election.getDescription());
        response.setElectionType(election.getElectionType());
        response.setStatus(election.getStatus());
        response.setElectionDate(election.getElectionDate());
        response.setStartDate(election.getStartDate());
        response.setEndDate(election.getEndDate());
        response.setRegistrationDeadline(election.getRegistrationDeadline());
        response.setTotalEligibleVoters(election.getTotalEligibleVoters());
        response.setTotalVotesCast(election.getTotalVotesCast());
        response.setTurnoutPercentage(election.getTurnoutPercentage());
        response.setCandidatesImported(election.isCandidatesImported());
        response.setCandidateCount((int) candidateRepository.countActiveByElection(election.getId()));
        response.setPartyCount((int) partyRepository.countActiveByElection(election.getId()));
        response.setBlockchainContractAddress(election.getBlockchainContractAddress());
        response.setCreatedAt(election.getCreatedAt());
        response.setLastSyncedAt(election.getLastSyncedAt());
        return response;
    }

    private CandidateResponse mapCandidateToResponse(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        response.setId(candidate.getId());
        response.setFirstName(candidate.getFirstName());
        response.setLastName(candidate.getLastName());
        response.setFullName(candidate.getFullName());
        response.setBiography(candidate.getBiography());
        response.setPhotoUrl(candidate.getPhotoUrl());
        response.setCounty(candidate.getCounty());
        response.setCountyName(candidate.getCounty() != null ? candidate.getCounty().getDisplayName() : null);
        response.setMunicipality(candidate.getMunicipality());
        response.setMunicipalityName(candidate.getMunicipality() != null ? candidate.getMunicipality().getDisplayName() : null);
        response.setPositionInList(candidate.getPositionInList());
        response.setIndependent(candidate.isIndependent());
        response.setProfession(candidate.getProfession());
        response.setAge(candidate.getAge());
        response.setEducation(candidate.getEducation());
        response.setPlatform(candidate.getPlatform());

        if (candidate.getParty() != null) {
            response.setPartyId(candidate.getParty().getId());
            response.setPartyName(candidate.getParty().getName());
            response.setPartyCode(candidate.getParty().getPartyCode());
        }

        return response;
    }

    private CandidateResponse mapSimpleCandidate(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        response.setId(candidate.getId());
        response.setFirstName(candidate.getFirstName());
        response.setLastName(candidate.getLastName());
        response.setFullName(candidate.getFullName());
        response.setCounty(candidate.getCounty());
        response.setMunicipality(candidate.getMunicipality());

        return response;
    }
    private PartyResponse mapPartyToResponse(Party party) {
        PartyResponse response = new PartyResponse();
        response.setId(party.getId());
        response.setPartyCode(party.getPartyCode());
        response.setName(party.getName());
        response.setDescription(party.getDescription());
        response.setLogoUrl(party.getLogoUrl());
        response.setColor(party.getColor());
        response.setLeader(party.getLeader());
        response.setListNumber(party.getListNumber());
        response.setCandidateCount((int) candidateRepository.countByParty(party.getId()));
        response.setCandidates(party.getCandidates().stream().map(this::mapSimpleCandidate).collect(Collectors.toList()));
        return response;
    }
}
