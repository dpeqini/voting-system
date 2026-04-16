package danjel.votingbackend.repository;

import danjel.votingbackend.dto.CandidateResultDto;
import danjel.votingbackend.dto.PartyResultDto;
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

    // Nëse Vote ka: private Election election;
    List<Vote> findByElection_Id(String electionId);
    Page<Vote> findByElection_Id(String electionId, Pageable pageable);

    @Query("SELECT v from Vote v join v.election where v.voteHash = :voteHash")
    Optional<Vote> findByVoteHash(@Param("voteHash") String voteHash);
    Optional<Vote> findByReceiptToken(String receiptToken);
    Optional<Vote> findByBlockchainTransactionId(String transactionId);

    boolean existsByVoterHashAndElection_Id(String voterHash, String electionId);

    // ── Totals ─────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId")
    long countVotesByElection(@Param("electionId") String electionId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.county = :county")
    long countVotesByElectionAndCounty(@Param("electionId") String electionId,
                                       @Param("county") AlbanianCounty county);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.election.id = :electionId AND v.municipality = :municipality")
    long countVotesByElectionAndMunicipality(@Param("electionId") String electionId,
                                             @Param("municipality") AlbanianMunicipality municipality);

    // ── Misc ───────────────────────────────────────────────────────────────

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.verified = false")
    List<Vote> findUnverifiedVotes(@Param("electionId") String electionId);

    @Query("SELECT v FROM Vote v WHERE v.election.id = :electionId AND v.timestamp BETWEEN :start AND :end")
    List<Vote> findByElectionAndTimeRange(@Param("electionId") String electionId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @Query("SELECT v FROM Vote v WHERE v.voteHash IN :hashes")
    List<Vote> findByVoteHashes(@Param("hashes") List<String> hashes);

    // ═══════════════════════════════════════════════════════════════════════
    // RESULTS — BY CANDIDATE
    // CandidateResultDto(Long, String, String, String, String, Long)
    // ═══════════════════════════════════════════════════════════════════════

    // PARLIAMENTARY (region = COUNTY)
    @Query("""
            SELECT new danjel.votingbackend.dto.CandidateResultDto(
                c.id,
                CONCAT(c.firstName, ' ', c.lastName),
                p.name,
                p.partyCode,
                CONCAT('', v.county),
                COUNT(v)
            )
            FROM Vote v
            JOIN v.candidate c
            LEFT JOIN c.party p
            WHERE v.election.id = :electionId
              AND v.candidate IS NOT NULL
            GROUP BY c.id, c.firstName, c.lastName, p.id, p.name, p.partyCode, v.county
            ORDER BY COUNT(v) DESC
            """)
    List<CandidateResultDto> findCandidateResults(@Param("electionId") String electionId);

    // PARLIAMENTARY filtered by county
    @Query("""
            SELECT new danjel.votingbackend.dto.CandidateResultDto(
                c.id,
                CONCAT(c.firstName, ' ', c.lastName),
                p.name,
                p.partyCode,
                CONCAT('', v.county),
                COUNT(v)
            )
            FROM Vote v
            JOIN v.candidate c
            LEFT JOIN c.party p
            WHERE v.election.id = :electionId
              AND v.county = :county
              AND v.candidate IS NOT NULL
            GROUP BY c.id, c.firstName, c.lastName, p.id, p.name, p.partyCode, v.county
            ORDER BY COUNT(v) DESC
            """)
    List<CandidateResultDto> findCandidateResultsByCounty(@Param("electionId") String electionId,
                                                          @Param("county") AlbanianCounty county);

    // LOCAL_GOVERNMENT (region = MUNICIPALITY) — pa filter
    @Query("""
            SELECT new danjel.votingbackend.dto.CandidateResultDto(
                c.id,
                CONCAT(c.firstName, ' ', c.lastName),
                p.name,
                p.partyCode,
                CONCAT('', v.municipality),
                COUNT(v)
            )
            FROM Vote v
            JOIN v.candidate c
            LEFT JOIN c.party p
            WHERE v.election.id = :electionId
              AND v.candidate IS NOT NULL
            GROUP BY c.id, c.firstName, c.lastName, p.id, p.name, p.partyCode, v.municipality
            ORDER BY COUNT(v) DESC
            """)
    List<CandidateResultDto> findCandidateResultsLocal(@Param("electionId") String electionId);

    // LOCAL_GOVERNMENT filtered by municipality
    @Query("""
            SELECT new danjel.votingbackend.dto.CandidateResultDto(
                c.id,
                CONCAT(c.firstName, ' ', c.lastName),
                p.name,
                p.partyCode,
                CONCAT('', v.municipality),
                COUNT(v)
            )
            FROM Vote v
            JOIN v.candidate c
            LEFT JOIN c.party p
            WHERE v.election.id = :electionId
              AND v.municipality = :municipality
              AND v.candidate IS NOT NULL
            GROUP BY c.id, c.firstName, c.lastName, p.id, p.name, p.partyCode, v.municipality
            ORDER BY COUNT(v) DESC
            """)
    List<CandidateResultDto> findCandidateResultsByMunicipality(@Param("electionId") String electionId,
                                                                @Param("municipality") AlbanianMunicipality municipality);

    // ═══════════════════════════════════════════════════════════════════════
    // RESULTS — BY PARTY
    // PartyResultDto(Long, String, String, Long)
    // ═══════════════════════════════════════════════════════════════════════

    @Query("""
            SELECT new danjel.votingbackend.dto.PartyResultDto(
                p.id,
                p.name,
                p.partyCode,
                COUNT(v)
            )
            FROM Vote v
            JOIN v.party p
            WHERE v.election.id = :electionId
            GROUP BY p.id, p.name, p.partyCode
            ORDER BY COUNT(v) DESC
            """)
    List<PartyResultDto> findPartyResults(@Param("electionId") String electionId);

    @Query("""
            SELECT new danjel.votingbackend.dto.PartyResultDto(
                p.id,
                p.name,
                p.partyCode,
                COUNT(v)
            )
            FROM Vote v
            JOIN v.party p
            WHERE v.election.id = :electionId
              AND v.county = :county
            GROUP BY p.id, p.name, p.partyCode
            ORDER BY COUNT(v) DESC
            """)
    List<PartyResultDto> findPartyResultsByCounty(@Param("electionId") String electionId,
                                                  @Param("county") AlbanianCounty county);

    @Query("""
            SELECT new danjel.votingbackend.dto.PartyResultDto(
                p.id,
                p.name,
                p.partyCode,
                COUNT(v)
            )
            FROM Vote v
            JOIN v.party p
            WHERE v.election.id = :electionId
              AND v.municipality = :municipality
            GROUP BY p.id, p.name, p.partyCode
            ORDER BY COUNT(v) DESC
            """)
    List<PartyResultDto> findPartyResultsByMunicipality(@Param("electionId") String electionId,
                                                        @Param("municipality") AlbanianMunicipality municipality);
}