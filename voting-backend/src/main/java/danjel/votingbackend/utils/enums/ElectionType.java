package danjel.votingbackend.utils.enums;

public enum ElectionType {
    PARLIAMENTARY("Parliamentary Elections"),
    LOCAL_GOVERNMENT("Local Government Elections");

    private final String displayName;

    ElectionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}