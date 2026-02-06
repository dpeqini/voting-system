package danjel.votingbackend.utils.enums;

public enum RegionEnum {
    TIRANE("Tiranë", "01"),
    DURRES("Durrës", "02"),
    ELBASAN("Elbasan", "03"),
    FIER("Fier", "04"),
    GJIROKASTER("Gjirokastër", "05"),
    KORCE("Korçë", "06"),
    KUKES("Kukës", "07"),
    LEZHE("Lezhë", "08"),
    SHKODER("Shkodër", "09"),
    VLORE("Vlorë", "10"),
    BERAT("Berat", "11"),
    DIBER("Dibër", "12");

    private final String displayName;
    private final String code;

    RegionEnum(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
}
