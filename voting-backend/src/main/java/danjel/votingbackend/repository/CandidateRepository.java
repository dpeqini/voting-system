package danjel.votingbackend.repository;

import danjel.votingbackend.model.Candidate;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, String> {

    List<Candidate> findByElectionId(String electionId);

    List<Candidate> findByElectionIdAndActive(String electionId, boolean active);

    List<Candidate> findByPartyId(String partyId);

    List<Candidate> findByCounty(AlbanianCounty county);

    List<Candidate> findByMunicipality(AlbanianMunicipality municipality);

    @Query("SELECT c FROM Candidate c WHERE c.election.id = :electionId AND c.county = :county AND c.active = true ORDER BY c.party.listNumber, c.positionInList")
    List<Candidate> findByElectionAndCounty(@Param("electionId") String electionId,
                                            @Param("county") AlbanianCounty county);

    @Query("SELECT c FROM Candidate c WHERE c.election.id = :electionId AND c.municipality = :municipality AND c.active = true ORDER BY c.party.listNumber, c.positionInList")
    List<Candidate> findByElectionAndMunicipality(@Param("electionId") String electionId,
                                                  @Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT c FROM Candidate c WHERE c.election.id = :electionId AND c.party.id = :partyId AND c.active = true ORDER BY c.positionInList")
    List<Candidate> findByElectionAndParty(@Param("electionId") String electionId,
                                           @Param("partyId") String partyId);

    @Query("SELECT c FROM Candidate c WHERE c.election.id = :electionId AND c.independent = true AND c.active = true")
    List<Candidate> findIndependentCandidates(@Param("electionId") String electionId);

    Optional<Candidate> findByExternalId(String externalId);

    @Query("SELECT COUNT(c) FROM Candidate c WHERE c.election.id = :electionId AND c.active = true")
    long countActiveByElection(@Param("electionId") String electionId);

    @Query("SELECT COUNT(c) FROM Candidate c WHERE c.party.id = :partyId AND c.active = true")
    long countByParty(@Param("partyId") String partyId);

    Page<Candidate> findByElectionId(String electionId, Pageable pageable);

    @Query("SELECT c FROM Candidate c WHERE c.election.id = :electionId AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Candidate> searchCandidates(@Param("electionId") String electionId,
                                     @Param("search") String search,
                                     Pageable pageable);

    void deleteByElectionId(String electionId);
}
