package danjel.votingbackend.model;

import danjel.votingbackend.utils.enums.AdminRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "admins", indexes = {
        @Index(name = "idx_admin_email", columnList = "email", unique = true)
})
public class Admin extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean accountLocked = false;

    @Column
    private int failedLoginAttempts = 0;

    public Admin() {}

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
