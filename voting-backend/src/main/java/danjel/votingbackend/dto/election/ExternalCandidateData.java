package danjel.votingbackend.dto.election;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ExternalCandidateData {

    // Getters and Setters
    private String externalId;
    private String firstName;
    private String lastName;
    private String biography;
    private String photoUrl;
    private String partyCode;
    private String countyCode;
    private String municipalityCode;
    private Integer positionInList;
    private boolean independent;
    private String profession;
    private Integer age;
    private String education;
    private String platform;

    // Constructors
    public ExternalCandidateData() {}

}