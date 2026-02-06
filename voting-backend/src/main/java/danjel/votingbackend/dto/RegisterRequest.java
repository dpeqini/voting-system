package danjel.votingbackend.dto;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class RegisterRequest {

    // Getters and Setters
    @NotBlank(message = "National ID is required")
    @Size(min = 10, max = 10, message = "National ID must be exactly 10 characters")
    private String nationalId;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase, one lowercase, one number, and one special character")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "County is required")
    private AlbanianCounty county;

    @NotNull(message = "Municipality is required")
    private AlbanianMunicipality municipality;

    @NotBlank(message = "Address is required")
    private String address;

    private String phoneNumber;

    @AssertTrue(message = "You must accept the terms and conditions")
    private boolean acceptedTerms;

    // Constructors
    public RegisterRequest() {}

    // Validation
    @AssertTrue(message = "Passwords must match")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    @AssertTrue(message = "Municipality must belong to the selected county")
    public boolean isMunicipalityValid() {
        if (municipality == null || county == null) return true;
        return municipality.getCounty() == county;
    }

}