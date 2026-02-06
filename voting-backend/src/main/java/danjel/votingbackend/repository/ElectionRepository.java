package danjel.votingbackend.repository;

import danjel.votingbackend.model.Election;
import danjel.votingbackend.utils.enums.ElectionStatus;
import danjel.votingbackend.utils.enums.ElectionType;
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
public interface ElectionRepository extends JpaRepository<Election, String> {

    List<Election> findByStatus(ElectionStatus status);

    List<Election> findByElectionType(ElectionType electionType);

    List<Election> findByStatusIn(List<ElectionStatus> statuses);

    Page<Election> findByStatusIn(List<ElectionStatus> statuses, Pageable pageable);

    @Query("SELECT e FROM Election e WHERE e.status = 'STARTED' AND e.startDate <= :now AND (e.endDate IS NULL OR e.endDate >= :now)")
    List<Election> findActiveElections(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Election e WHERE e.electionDate BETWEEN :startDate AND :endDate")
    List<Election> findElectionsBetweenDates(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    @Query("SELECT e FROM Election e WHERE e.status = :status AND e.electionType = :type")
    List<Election> findByStatusAndType(@Param("status") ElectionStatus status,
                                       @Param("type") ElectionType type);

    @Query("SELECT e FROM Election e WHERE e.electionDate >= :now ORDER BY e.electionDate ASC")
    List<Election> findUpcomingElections(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Election e WHERE e.status = 'CLOSED' ORDER BY e.endDate DESC")
    List<Election> findClosedElections();

    @Query("SELECT e FROM Election e WHERE e.candidatesImported = false")
    List<Election> findElectionsWithoutCandidates();

    @Query("SELECT COUNT(e) FROM Election e WHERE e.status = :status")
    long countByStatus(@Param("status") ElectionStatus status);

    @Query("SELECT e FROM Election e WHERE e.registrationDeadline > :now AND e.status = 'CREATED'")
    List<Election> findElectionsOpenForRegistration(@Param("now") LocalDateTime now);

    Optional<Election> findByBlockchainContractAddress(String contractAddress);

    @Query("SELECT e FROM Election e LEFT JOIN FETCH e.candidates WHERE e.id = :id")
    Optional<Election> findByIdWithCandidates(@Param("id") String id);

    @Query("SELECT e FROM Election e LEFT JOIN FETCH e.parties WHERE e.id = :id")
    Optional<Election> findByIdWithParties(@Param("id") String id);

    @Query("SELECT e FROM Election e LEFT JOIN FETCH e.candidates LEFT JOIN FETCH e.parties WHERE e.id = :id")
    Optional<Election> findByIdWithCandidatesAndParties(@Param("id") String id);
}
