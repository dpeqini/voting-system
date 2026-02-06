package danjel.votingbackend.utils.enums;

public enum AlbanianCounty {
    BERAT("Berat", "01"),
    DIBER("Dibër", "02"),
    DURRES("Durrës", "03"),
    ELBASAN("Elbasan", "04"),
    FIER("Fier", "05"),
    GJIROKASTER("Gjirokastër", "06"),
    KORCE("Korçë", "07"),
    KUKES("Kukës", "08"),
    LEZHE("Lezhë", "09"),
    SHKODER("Shkodër", "10"),
    TIRANE("Tiranë", "11"),
    VLORE("Vlorë", "12");

    private final String displayName;
    private final String code;

    AlbanianCounty(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static AlbanianCounty fromCode(String code) {
        for (AlbanianCounty county : values()) {
            if (county.getCode().equals(code)) {
                return county;
            }
        }
        throw new IllegalArgumentException("Unknown county code: " + code);
    }
}