package danjel.votingbackend.repository;

import danjel.votingbackend.model.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for DeviceRegistration.
 *
 * Spring generates all SQL automatically from the method names.
 * You do NOT write any implementation — just declare the interface.
 */
@Repository
public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, String> {

    /** Primary lookup — finds a device by its unique device ID string. */
    Optional<DeviceRegistration> findByDeviceId(String deviceId);

    /** Find all devices registered to a given voter (useful for audit / revocation). */
    List<DeviceRegistration> findAllByVoterId(UUID voterId);

    /** Revoke all devices for a voter — call this on account lockout. */
    @Modifying
    @Transactional
    void deleteAllByVoterId(UUID voterId);

    /** Update last-seen timestamp without loading the full entity. */
    @Modifying
    @Transactional
    @Query("UPDATE DeviceRegistration d SET d.lastSeenAt = :now WHERE d.deviceId = :deviceId")
    void updateLastSeen(String deviceId, Instant now);
}
