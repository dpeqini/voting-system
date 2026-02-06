package danjel.votingbackend.utils.enums;


public enum UserRole {
    ADMIN("ADMIN"),
    VOTER("VOTER"),
    ELECTION_OFFICIAL("ELECTION_OFFICIAL"),
    OBSERVER("OBSERVER");

    private  final String displayName;
    UserRole( String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
