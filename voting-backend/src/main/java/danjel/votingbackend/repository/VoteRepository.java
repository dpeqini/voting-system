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

    Optional<Vote> findByReceiptToken(String receiptToken);

    Optional<Vote> findByBlockchainTransactionId(String transactionId);

    boolean existsByVoterHashAndElectionId(String voterHash, String electionId);

    // ── Aggregate counts ──────────────────────────────────────────────────────

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId")
    long countByElection(@Param("electionId") String electionId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.county = :county")
    long countByElectionAndCounty(@Param("electionId") String electionId,
                                  @Param("county") AlbanianCounty county);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.municipality = :municipality")
    long countByElectionAndMunicipality(@Param("electionId") String electionId,
                                        @Param("municipality") AlbanianMunicipality municipality);

    /**
     * Count votes per candidate — uses FK relation (v.candidate.id).
     */
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.candidate.id = :candidateId")
    long countByCandidateInElection(@Param("electionId") String electionId,
                                    @Param("candidateId") String candidateId);

    /**
     * Count votes per party — uses FK relation (v.party.id).
     */
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.party.id = :partyId")
    long countByPartyInElection(@Param("electionId") String electionId,
                                @Param("partyId") String partyId);

    /**
     * Aggregate results: candidate id → vote count, ordered by votes desc.
     * Useful for building election results.
     */
    @Query("""
            SELECT v.candidate.id, COUNT(v)
            FROM Vote v
            WHERE v.election.id = :electionId AND v.candidate IS NOT NULL
            GROUP BY v.candidate.id
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countVotesByCandidateInElection(@Param("electionId") String electionId);

    /**
     * Aggregate results: party id → vote count, ordered by votes desc.
     */
    @Query("""
            SELECT v.party.id, COUNT(v)
            FROM Vote v
            WHERE v.election.id = :electionId AND v.party IS NOT NULL
            GROUP BY v.party.id
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countVotesByPartyInElection(@Param("electionId") String electionId);

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.verified = false")
    List<Vote> findUnverifiedVotes(@Param("electionId") String electionId);

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.timestamp BETWEEN :start AND :end")
    List<Vote> findByElectionAndTimeRange(@Param("electionId") String electionId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * Find a block's votes by their hashes — used for Merkle-proof lookups.
     */
    @Query("SELECT v FROM Vote v WHERE v.voteHash IN :hashes")
    List<Vote> findByVoteHashes(@Param("hashes") List<String> hashes);
}