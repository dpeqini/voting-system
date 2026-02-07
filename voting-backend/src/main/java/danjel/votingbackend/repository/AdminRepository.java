package danjel.votingbackend.repository;

import danjel.votingbackend.model.Admin;
import danjel.votingbackend.utils.enums.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, String> {

    Optional<Admin> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Admin> findByRole(AdminRole role);

    @Modifying
    @Query("UPDATE Admin a SET a.failedLoginAttempts = 0, a.accountLocked = false WHERE a.id = :adminId")
    void resetLoginAttempts(@Param("adminId") String adminId);

    @Modifying
    @Query("UPDATE Admin a SET a.failedLoginAttempts = a.failedLoginAttempts + 1 WHERE a.id = :adminId")
    void incrementFailedLoginAttempts(@Param("adminId") String adminId);

    @Modifying
    @Query("UPDATE Admin a SET a.accountLocked = true WHERE a.id = :adminId")
    void lockAccount(@Param("adminId") String adminId);
}
