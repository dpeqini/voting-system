package danjel.votingbackend.utils.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum AlbanianMunicipality {
    // Berat County
    BERAT("Berat", AlbanianCounty.BERAT),
    KUCOVE("Kuçovë", AlbanianCounty.BERAT),
    POLICAN("Poliçan", AlbanianCounty.BERAT),
    SKRAPAR("Skrapar", AlbanianCounty.BERAT),
    URA_VAJGURORE("Ura Vajgurore", AlbanianCounty.BERAT),

    // Dibër County
    BULQIZE("Bulqizë", AlbanianCounty.DIBER),
    DIBER("Dibër", AlbanianCounty.DIBER),
    KLOS("Klos", AlbanianCounty.DIBER),
    MAT("Mat", AlbanianCounty.DIBER),

    // Durrës County
    DURRES("Durrës", AlbanianCounty.DURRES),
    KRUJE("Krujë", AlbanianCounty.DURRES),
    SHIJAK("Shijak", AlbanianCounty.DURRES),

    // Elbasan County
    BELSH("Belsh", AlbanianCounty.ELBASAN),
    CERRIK("Cërrik", AlbanianCounty.ELBASAN),
    ELBASAN("Elbasan", AlbanianCounty.ELBASAN),
    GRAMSH("Gramsh", AlbanianCounty.ELBASAN),
    LIBRAZHD("Librazhd", AlbanianCounty.ELBASAN),
    PEQIN("Peqin", AlbanianCounty.ELBASAN),
    PRRENJAS("Prrenjas", AlbanianCounty.ELBASAN),

    // Fier County
    DIVJAKE("Divjakë", AlbanianCounty.FIER),
    FIER("Fier", AlbanianCounty.FIER),
    LUSHNJE("Lushnje", AlbanianCounty.FIER),
    MALLAKASTER("Mallakastër", AlbanianCounty.FIER),
    PATOS("Patos", AlbanianCounty.FIER),
    ROSKOVEC("Roskovec", AlbanianCounty.FIER),

    // Gjirokastër County
    DROPULL("Dropull", AlbanianCounty.GJIROKASTER),
    GJIROKASTER("Gjirokastër", AlbanianCounty.GJIROKASTER),
    KELCYRE("Këlcyrë", AlbanianCounty.GJIROKASTER),
    LIBOHOVE("Libohovë", AlbanianCounty.GJIROKASTER),
    MEMALIAJ("Memaliaj", AlbanianCounty.GJIROKASTER),
    PERMET("Përmet", AlbanianCounty.GJIROKASTER),
    TEPELENE("Tepelenë", AlbanianCounty.GJIROKASTER),

    // Korçë County
    DEVOLL("Devoll", AlbanianCounty.KORCE),
    KOLONJE("Kolonjë", AlbanianCounty.KORCE),
    KORCE("Korçë", AlbanianCounty.KORCE),
    MALIQ("Maliq", AlbanianCounty.KORCE),
    POGRADEC("Pogradec", AlbanianCounty.KORCE),
    PUSTEC("Pustec", AlbanianCounty.KORCE),

    // Kukës County
    HAS("Has", AlbanianCounty.KUKES),
    KUKES("Kukës", AlbanianCounty.KUKES),
    TROPOJE("Tropojë", AlbanianCounty.KUKES),

    // Lezhë County
    KURBIN("Kurbin", AlbanianCounty.LEZHE),
    LEZHE("Lezhë", AlbanianCounty.LEZHE),
    MIRDITE("Mirditë", AlbanianCounty.LEZHE),

    // Shkodër County
    FUSHE_ARREZ("Fushë-Arrëz", AlbanianCounty.SHKODER),
    MALESI_E_MADHE("Malësi e Madhe", AlbanianCounty.SHKODER),
    PUKE("Pukë", AlbanianCounty.SHKODER),
    SHKODER("Shkodër", AlbanianCounty.SHKODER),
    VAU_DEJES("Vau i Dejës", AlbanianCounty.SHKODER),

    // Tiranë County
    KAMEZ("Kamëz", AlbanianCounty.TIRANE),
    KAVAJE("Kavajë", AlbanianCounty.TIRANE),
    RROGOZHINE("Rrogozhinë", AlbanianCounty.TIRANE),
    TIRANE("Tiranë", AlbanianCounty.TIRANE),
    VORE("Vorë", AlbanianCounty.TIRANE),

    // Vlorë County
    DELVINE("Delvinë", AlbanianCounty.VLORE),
    FINIQ("Finiq", AlbanianCounty.VLORE),
    HIMARA("Himarë", AlbanianCounty.VLORE),
    KONISPOL("Konispol", AlbanianCounty.VLORE),
    SARANDE("Sarandë", AlbanianCounty.VLORE),
    SELENICE("Selenicë", AlbanianCounty.VLORE),
    VLORE("Vlorë", AlbanianCounty.VLORE),

    OTHER("Tjeter", AlbanianCounty.TIRANE);
    private final String displayName;
    private final AlbanianCounty county;

    @JsonCreator
    public static AlbanianMunicipality fromString(String value) {
        if (value == null) return null;
        return Arrays.stream(AlbanianMunicipality.values())
                .filter(m -> m.name().equalsIgnoreCase(value) || m.displayName.equalsIgnoreCase(value))
                .findFirst()
                .orElse(AlbanianMunicipality.OTHER);
    }
    AlbanianMunicipality(String displayName, AlbanianCounty county) {
        this.displayName = displayName;
        this.county = county;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AlbanianCounty getCounty() {
        return county;
    }

    public static AlbanianMunicipality[] getByCounty(AlbanianCounty county) {
        return java.util.Arrays.stream(values())
                .filter(m -> m.getCounty() == county)
                .toArray(AlbanianMunicipality[]::new);
    }
}
