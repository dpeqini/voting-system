package danjel.votingbackend.dto.election;


import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CandidateResponse {

    // Getters and Setters
    private String id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String biography;
    private String photoUrl;
    private String partyId;
    private String partyName;
    private String partyCode;
    private AlbanianCounty county;
    private String countyName;
    private AlbanianMunicipality municipality;
    private String municipalityName;
    private Integer positionInList;
    private boolean independent;
    private String profession;
    private Integer age;
    private String education;
    private String platform;

    // Constructors
    public CandidateResponse() {}

}