package danjel.votingbackend.repository;

import danjel.votingbackend.model.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, String> {

    List<Party> findByElectionId(String electionId);

    List<Party> findByElectionIdAndActive(String electionId, boolean active);

    Optional<Party> findByPartyCode(String partyCode);

    Optional<Party> findByExternalId(String externalId);

    Optional<Party> findByElectionIdAndPartyCode(String electionId, String partyCode);

    @Query("SELECT p FROM Party p WHERE p.election.id = :electionId AND p.active = true ORDER BY p.listNumber")
    List<Party> findActivePartiesByElection(@Param("electionId") String electionId);

    @Query("SELECT p FROM Party p LEFT JOIN FETCH p.candidates WHERE p.election.id = :electionId AND p.active = true ORDER BY p.listNumber")
    List<Party> findPartiesWithCandidates(@Param("electionId") String electionId);

    @Query("SELECT COUNT(p) FROM Party p WHERE p.election.id = :electionId AND p.active = true")
    long countActiveByElection(@Param("electionId") String electionId);

    boolean existsByPartyCodeAndElectionId(String partyCode, String electionId);

    void deleteByElectionId(String electionId);
}