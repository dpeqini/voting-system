package danjel.votingbackend.utils.enums;


public enum AdminRole {
    ADMIN("ADMIN"),
    ELECTION_OFFICIAL("ELECTION_OFFICIAL"),
    OBSERVER("OBSERVER");

    private final String displayName;

    AdminRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
