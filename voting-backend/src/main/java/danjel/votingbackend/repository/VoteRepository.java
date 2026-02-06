package danjel.votingbackend.repository;

import danjel.votingbackend.model.Vote;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, String> {

    List<Vote> findByElectionId(String electionId);

    Page<Vote> findByElectionId(String electionId, Pageable pageable);

    Optional<Vote> findByVoteHash(String voteHash);

    Optional<Vote> findByBlockchainTransactionId(String transactionId);

    boolean existsByVoterHashAndElectionId(String voterHash, String electionId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId")
    long countByElection(@Param("electionId") String electionId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.county = :county")
    long countByElectionAndCounty(@Param("electionId") String electionId,
                                  @Param("county") AlbanianCounty county);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.municipality = :municipality")
    long countByElectionAndMunicipality(@Param("electionId") String electionId,
                                        @Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.candidateId = :candidateId")
    long countByCandidateInElection(@Param("electionId") String electionId,
                                    @Param("candidateId") String candidateId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.partyId = :partyId")
    long countByPartyInElection(@Param("electionId") String electionId,
                                @Param("partyId") String partyId);

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.verified = false")
    List<Vote> findUnverifiedVotes(@Param("electionId") String electionId);

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.timestamp BETWEEN :startTime AND :endTime")
    List<Vote> findVotesBetweenTimes(@Param("electionId") String electionId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    @Query("SELECT v.county, COUNT(v) FROM Vote v WHERE v.election.id = :electionId GROUP BY v.county")
    List<Object[]> countVotesByCounty(@Param("electionId") String electionId);

    @Query("SELECT v.municipality, COUNT(v) FROM Vote v WHERE v.election.id = :electionId GROUP BY v.municipality")
    List<Object[]> countVotesByMunicipality(@Param("electionId") String electionId);

    @Query("SELECT v.candidateId, COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.candidateId IS NOT NULL GROUP BY v.candidateId ORDER BY COUNT(v) DESC")
    List<Object[]> getVoteCountByCandidate(@Param("electionId") String electionId);

    @Query("SELECT v.partyId, COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.partyId IS NOT NULL GROUP BY v.partyId ORDER BY COUNT(v) DESC")
    List<Object[]> getVoteCountByParty(@Param("electionId") String electionId);

    @Query("SELECT v FROM Vote v WHERE v.blockNumber = :blockNumber")
    List<Vote> findByBlockNumber(@Param("blockNumber") Long blockNumber);
}