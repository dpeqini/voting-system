package danjel.votingbackend.service;

import danjel.votingbackend.dto.election.ExternalCandidateData;
import danjel.votingbackend.dto.election.ExternalPartyData;
import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.model.Election;
import danjel.votingbackend.model.Party;
import danjel.votingbackend.repository.CandidateRepository;
import danjel.votingbackend.repository.PartyRepository;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class ExternalDataService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalDataService.class);

    private final PartyRepository partyRepository;
    private final CandidateRepository candidateRepository;
    private final RestTemplate restTemplate;

    @Value("${external.api.parties-url:}")
    private String partiesApiUrl;

    @Value("${external.api.candidates-url:}")
    private String candidatesApiUrl;

    @Value("${external.api.api-key:}")
    private String apiKey;

    public ExternalDataService(PartyRepository partyRepository,
                               CandidateRepository candidateRepository) {
        this.partyRepository = partyRepository;
        this.candidateRepository = candidateRepository;
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public ImportResult importPartiesAndCandidates(Election election, String dataSourceUrl) {
        ImportResult result = new ImportResult();

        try {
            // Import parties first
            List<ExternalPartyData> partyDataList = fetchParties(dataSourceUrl);
            for (ExternalPartyData partyData : partyDataList) {
                Party party = importParty(election, partyData);
                result.addParty(party);

                // Import candidates for this party
                if (partyData.getCandidates() != null) {
                    for (ExternalCandidateData candidateData : partyData.getCandidates()) {
                        candidateData.setPartyCode(partyData.getPartyCode());
                        Candidate candidate = importCandidate(election, candidateData, party);
                        result.addCandidate(candidate);
                    }
                }
            }

            // Import independent candidates
            List<ExternalCandidateData> independentCandidates = fetchIndependentCandidates(dataSourceUrl);
            for (ExternalCandidateData candidateData : independentCandidates) {
                candidateData.setPartyCode(null);
                Candidate candidate = importCandidate(election, candidateData, null);
                candidate.setIndependent(true);
                candidateRepository.save(candidate);
                result.addCandidate(candidate);
            }

            election.setCandidatesImported(true);
            election.setLastSyncedAt(LocalDateTime.now());
            election.setExternalDataSource(dataSourceUrl);

            result.setSuccess(true);
            result.setMessage("Successfully imported " + result.getPartiesImported() +
                    " parties and " + result.getCandidatesImported() + " candidates");

            logger.info("Import completed for election {}: {} parties, {} candidates",
                    election.getId(), result.getPartiesImported(), result.getCandidatesImported());

        } catch (Exception e) {
            logger.error("Failed to import data for election {}: {}", election.getId(), e.getMessage());
            result.setSuccess(false);
            result.setMessage("Import failed: " + e.getMessage());
        }

        return result;
    }

    private List<ExternalPartyData> fetchParties(String dataSourceUrl) {
        if (dataSourceUrl == null || dataSourceUrl.isEmpty()) {
            // Return mock data for development
            return generateMockParties();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<ExternalPartyData[]> response = restTemplate.exchange(
                    dataSourceUrl + "/parties",
                    HttpMethod.GET,
                    entity,
                    ExternalPartyData[].class
            );

            return Arrays.asList(response.getBody());
        } catch (Exception e) {
            logger.warn("Failed to fetch parties from external API, using mock data: {}", e.getMessage());
            return generateMockParties();
        }
    }

    private List<ExternalCandidateData> fetchIndependentCandidates(String dataSourceUrl) {
        if (dataSourceUrl == null || dataSourceUrl.isEmpty()) {
            return generateMockIndependentCandidates();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<ExternalCandidateData[]> response = restTemplate.exchange(
                    dataSourceUrl + "/candidates/independent",
                    HttpMethod.GET,
                    entity,
                    ExternalCandidateData[].class
            );

            return Arrays.asList(response.getBody());
        } catch (Exception e) {
            logger.warn("Failed to fetch independent candidates: {}", e.getMessage());
            return generateMockIndependentCandidates();
        }
    }

    @Transactional
    public Party importParty(Election election, ExternalPartyData data) {
        Optional<Party> existingParty = partyRepository.findByElectionIdAndPartyCode(
                election.getId(), data.getPartyCode()
        );

        Party party = existingParty.orElse(new Party());
        party.setPartyCode(data.getPartyCode());
        party.setName(data.getName());
        party.setDescription(data.getDescription());
        party.setLogoUrl(data.getLogoUrl());
        party.setColor(data.getColor());
        party.setLeader(data.getLeader());
        party.setListNumber(data.getListNumber());
        party.setExternalId(data.getExternalId());
        party.setElection(election);
        party.setActive(true);

        return partyRepository.save(party);
    }

    @Transactional
    public Candidate importCandidate(Election election, ExternalCandidateData data, Party party) {
        Candidate candidate = new Candidate(data.getFirstName(), data.getLastName(), election);
        candidate.setBiography(data.getBiography());
        candidate.setPhotoUrl(data.getPhotoUrl());
        candidate.setExternalId(data.getExternalId());
        candidate.setPositionInList(data.getPositionInList());
        candidate.setIndependent(data.isIndependent());
        candidate.setProfession(data.getProfession());
        candidate.setAge(data.getAge());
        candidate.setEducation(data.getEducation());
        candidate.setPlatform(data.getPlatform());
        candidate.setParty(party);
        candidate.setActive(true);

        // Set county/municipality based on election type and data
        if (data.getCountyCode() != null) {
            try {
                candidate.setCounty(AlbanianCounty.fromCode(data.getCountyCode()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid county code: {}", data.getCountyCode());
            }
        }

        if (data.getMunicipalityCode() != null) {
            try {
                candidate.setMunicipality(AlbanianMunicipality.valueOf(data.getMunicipalityCode()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid municipality code: {}", data.getMunicipalityCode());
            }
        }

        return candidateRepository.save(candidate);
    }

    private List<ExternalPartyData> generateMockParties() {
        List<ExternalPartyData> parties = new ArrayList<>();

        // Socialist Party
        ExternalPartyData ps = new ExternalPartyData();
        ps.setExternalId("PS001");
        ps.setPartyCode("PS");
        ps.setName("Partia Socialiste e Shqipërisë");
        ps.setDescription("Socialist Party of Albania");
        ps.setColor("#E31B23");
        ps.setLeader("Edi Rama");
        ps.setListNumber(1);
        ps.setCandidates(generateMockCandidatesForParty("PS"));
        parties.add(ps);

        // Democratic Party
        ExternalPartyData pd = new ExternalPartyData();
        pd.setExternalId("PD001");
        pd.setPartyCode("PD");
        pd.setName("Partia Demokratike e Shqipërisë");
        pd.setDescription("Democratic Party of Albania");
        pd.setColor("#0066CC");
        pd.setLeader("Sali Berisha");
        pd.setListNumber(2);
        pd.setCandidates(generateMockCandidatesForParty("PD"));
        parties.add(pd);

        // Socialist Movement for Integration
        ExternalPartyData lsi = new ExternalPartyData();
        lsi.setExternalId("LSI001");
        lsi.setPartyCode("LSI");
        lsi.setName("Lëvizja Socialiste për Integrim");
        lsi.setDescription("Socialist Movement for Integration");
        lsi.setColor("#00AA00");
        lsi.setLeader("Monika Kryemadhi");
        lsi.setListNumber(3);
        lsi.setCandidates(generateMockCandidatesForParty("LSI"));
        parties.add(lsi);

        return parties;
    }

    private List<ExternalCandidateData> generateMockCandidatesForParty(String partyCode) {
        List<ExternalCandidateData> candidates = new ArrayList<>();

        for (AlbanianCounty county : AlbanianCounty.values()) {
            for (int i = 1; i <= 5; i++) {
                ExternalCandidateData candidate = new ExternalCandidateData();
                candidate.setExternalId(partyCode + "-" + county.getCode() + "-" + i);
                candidate.setFirstName("Kandidat" + i);
                candidate.setLastName(county.getDisplayName() + " " + partyCode);
                candidate.setCountyCode(county.getCode());
                candidate.setPositionInList(i);
                candidate.setProfession("Politician");
                candidate.setAge(35 + i);
                candidate.setEducation("University Degree");
                candidate.setPlatform("Working for the people of " + county.getDisplayName());
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private List<ExternalCandidateData> generateMockIndependentCandidates() {
        List<ExternalCandidateData> independents = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            AlbanianCounty county = AlbanianCounty.values()[i];
            ExternalCandidateData candidate = new ExternalCandidateData();
            candidate.setExternalId("IND-" + county.getCode() + "-1");
            candidate.setFirstName("Independent" + (i + 1));
            candidate.setLastName("Candidate");
            candidate.setCountyCode(county.getCode());
            candidate.setIndependent(true);
            candidate.setPositionInList(1);
            candidate.setProfession("Civil Society Activist");
            candidate.setAge(45 + i);
            independents.add(candidate);
        }

        return independents;
    }

    @Getter
    public static class ImportResult {
        private boolean success;
        private String message;
        private List<Party> parties = new ArrayList<>();
        private List<Candidate> candidates = new ArrayList<>();

        public void addParty(Party party) {
            parties.add(party);
        }

        public void addCandidate(Candidate candidate) {
            candidates.add(candidate);
        }

        public int getPartiesImported() {
            return parties.size();
        }

        public int getCandidatesImported() {
            return candidates.size();
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }
}