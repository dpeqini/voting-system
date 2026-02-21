package danjel.votingbackend.repository;

import danjel.votingbackend.model.Voter;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoterRepository extends JpaRepository<Voter, String> {

    // ── Lookups ───────────────────────────────────────────────────────────────

    Optional<Voter> findByNationalId(String nationalId);

    boolean existsByNationalId(String nationalId);

    List<Voter> findByCounty(AlbanianCounty county);

    List<Voter> findByMunicipality(AlbanianMunicipality municipality);

    // ── Admin / reporting queries ─────────────────────────────────────────────

    Page<Voter> findByEnabledTrue(Pageable pageable);

    Page<Voter> findByEnabledFalse(Pageable pageable);

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.enabled = true")
    long countEligibleVoters();

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.enabled = true AND v.county = :county")
    long countEligibleVotersByCounty(@Param("county") AlbanianCounty county);

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.enabled = true AND v.municipality = :municipality")
    long countEligibleVotersByMunicipality(@Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT v FROM Voter v WHERE v.enabled = true AND v.county = :county")
    List<Voter> findEligibleVotersByCounty(@Param("county") AlbanianCounty county);

    @Query("SELECT v FROM Voter v WHERE v.enabled = true AND v.municipality = :municipality")
    List<Voter> findEligibleVotersByMunicipality(@Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT v FROM Voter v WHERE LOWER(v.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(v.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Voter> searchByName(@Param("name") String name, Pageable pageable);

    @Query("SELECT COUNT(v) FROM Voter v WHERE :electionId MEMBER OF v.votedElectionIds")
    long countVotersWhoVotedInElection(@Param("electionId") String electionId);

    // ── Updates ───────────────────────────────────────────────────────────────

    @Modifying
    @Query("UPDATE Voter v SET v.lastAuthenticatedAt = :ts WHERE v.id = :voterId")
    void updateLastAuthenticated(@Param("voterId") String voterId,
                                 @Param("ts") LocalDateTime ts);

    @Modifying
    @Query("UPDATE Voter v SET v.enabled = false WHERE v.id = :voterId")
    void disableVoter(@Param("voterId") String voterId);

    @Modifying
    @Query("UPDATE Voter v SET v.enabled = true WHERE v.id = :voterId")
    void enableVoter(@Param("voterId") String voterId);
}