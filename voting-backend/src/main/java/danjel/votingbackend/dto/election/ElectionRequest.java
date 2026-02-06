package danjel.votingbackend.dto.election;

import danjel.votingbackend.utils.enums.ElectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class ElectionRequest {

    // Getters and Setters
    @NotBlank(message = "Election name is required")
    private String name;

    private String description;

    @NotNull(message = "Election type is required")
    private ElectionType electionType;

    @NotNull(message = "Election date is required")
    private LocalDateTime electionDate;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @NotNull(message = "Registration deadline is required")
    private LocalDateTime registrationDeadline;

    private String externalDataSource;

    // Constructors
    public ElectionRequest() {}

}
