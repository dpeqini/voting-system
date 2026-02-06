package danjel.votingbackend.dto.election;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class PartyResponse {

    // Getters and Setters
    private String id;
    private String partyCode;
    private String name;
    private String description;
    private String logoUrl;
    private String color;
    private String leader;
    private int listNumber;
    private int candidateCount;
    private List<CandidateResponse> candidates;

    // Constructors
    public PartyResponse() {}

}
