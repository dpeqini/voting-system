package danjel.votingbackend.repository;

import danjel.votingbackend.model.Voter;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import danjel.votingbackend.utils.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoterRepository extends JpaRepository<Voter, String> {

    Optional<Voter> findByEmail(String email);

    Optional<Voter> findByNationalId(String nationalId);

    boolean existsByEmail(String email);

    boolean existsByNationalId(String nationalId);

    List<Voter> findByCounty(AlbanianCounty county);

    List<Voter> findByMunicipality(AlbanianMunicipality municipality);

    List<Voter> findByRole(UserRole role);

    Page<Voter> findByVerifiedTrue(Pageable pageable);

    Page<Voter> findByVerifiedFalse(Pageable pageable);

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.verified = true AND v.enabled = true")
    long countEligibleVoters();

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.verified = true AND v.enabled = true AND v.county = :county")
    long countEligibleVotersByCounty(@Param("county") AlbanianCounty county);

    @Query("SELECT COUNT(v) FROM Voter v WHERE v.verified = true AND v.enabled = true AND v.municipality = :municipality")
    long countEligibleVotersByMunicipality(@Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT v FROM Voter v WHERE v.verified = true AND v.enabled = true AND v.county = :county")
    List<Voter> findEligibleVotersByCounty(@Param("county") AlbanianCounty county);

    @Query("SELECT v FROM Voter v WHERE v.verified = true AND v.enabled = true AND v.municipality = :municipality")
    List<Voter> findEligibleVotersByMunicipality(@Param("municipality") AlbanianMunicipality municipality);

    @Query("SELECT v FROM Voter v WHERE LOWER(v.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(v.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Voter> searchByName(@Param("name") String name, Pageable pageable);

    @Modifying
    @Query("UPDATE Voter v SET v.failedLoginAttempts = 0, v.accountLocked = false WHERE v.id = :voterId")
    void resetLoginAttempts(@Param("voterId") String voterId);

    @Modifying
    @Query("UPDATE Voter v SET v.failedLoginAttempts = v.failedLoginAttempts + 1 WHERE v.id = :voterId")
    void incrementFailedLoginAttempts(@Param("voterId") String voterId);

    @Modifying
    @Query("UPDATE Voter v SET v.accountLocked = true WHERE v.id = :voterId")
    void lockAccount(@Param("voterId") String voterId);

    @Query("SELECT v FROM Voter v WHERE v.faceVerified = false AND v.verified = true")
    List<Voter> findVotersPendingFaceVerification();

    @Query("SELECT COUNT(v) FROM Voter v WHERE :electionId MEMBER OF v.votedElectionIds")
    long countVotersWhoVotedInElection(@Param("electionId") String electionId);
}
