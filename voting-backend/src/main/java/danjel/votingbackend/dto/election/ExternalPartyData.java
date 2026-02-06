package danjel.votingbackend.dto.election;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExternalPartyData {

    // Getters and Setters
    private String externalId;
    private String partyCode;
    private String name;
    private String description;
    private String logoUrl;
    private String color;
    private String leader;
    private int listNumber;
    private List<ExternalCandidateData> candidates;

    // Constructors
    public ExternalPartyData() {}

}