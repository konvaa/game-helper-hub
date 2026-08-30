package cz.libor.gamehelper.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Čistý JUnit test {@link Seg5} - PLAN_P1_SPRING_SLICE.md sekce 7.2,
 * "kontrolní tabulka z 1.3". Žádný Spring, žádná databáze - {@link Seg5} je
 * čistá funkce nad pěti čísly, test to využívá naplno (běží v milisekundách).
 *
 * <p>Koeficienty použité v {@code @CsvSource} ({@code eMin=0},
 * {@code eMinLev1..5 = 0,1,2,3,4}) jsou konkrétní hodnoty skillu Raise
 * Skeleton (ověřeno v {@code data/.../skills_raw.json}) - {@link Seg5} sám
 * o sobě je na konkrétním skillu nezávislý (viz jeho javadoc), ale kontrolní
 * tabulka v plánu je psaná právě pro tuhle sadu koeficientů, takže test ji
 * kopíruje jedna ku jedné, aby šla přímo porovnat s
 * {@code PLAN_P1_SPRING_SLICE.md} sekcí 1.3.
 */
class Seg5Test {

    private static final int E_MIN = 0;
    private static final int E_MIN_LEV1 = 0;
    private static final int E_MIN_LEV2 = 1;
    private static final int E_MIN_LEV3 = 2;
    private static final int E_MIN_LEV4 = 3;
    private static final int E_MIN_LEV5 = 4;

    /**
     * Kontrolní tabulka z PLAN_P1_SPRING_SLICE.md sekce 1.3/7.2:
     * {@code seg5(16)=8, seg5(20)=16, seg5(29)=42, seg5(40)=86, seg5(50)=126}.
     * Level 29 je schválně první hodnota NAD stropem 28 - ukazuje, že
     * poslední segment (EMinLev5) se do součtu dostal.
     */
    @ParameterizedTest(name = "seg5({0}) = {1}")
    @CsvSource({
            "16, 8",
            "20, 16",
            "29, 42",
            "40, 86",
            "50, 126"
    })
    void odpovídáKontrolníTabulceZPlánu(int level, int expected) {
        assertEquals(expected, Seg5.compute(level, E_MIN, E_MIN_LEV1, E_MIN_LEV2, E_MIN_LEV3, E_MIN_LEV4, E_MIN_LEV5));
    }

    /**
     * Hranice mezi segmenty - ověřuje, že se {@code min}/{@code max} ořezy
     * v {@link Seg5} spouští přesně tam, kde mají (ne o jednu úroveň vedle).
     * Segmenty podle PLAN_P1_SPRING_SLICE.md 1.3: 2-8, 9-16, 17-22, 23-28, 29+.
     */
    @ParameterizedTest(name = "seg5({0}) = {1}")
    @CsvSource({
            "1, 0",     // před prvním segmentem - jen EMin (EMinLev1 je u Raise Skeleton 0, viz javadoc třídy)
            "8, 0",     // konec segmentu 2-8 - EMinLev1=0, takže i tady je součet pořád 0
            "9, 1",     // začátek segmentu 9-16 - první úroveň, kde EMinLev2 přispěje
            "16, 8",    // konec segmentu 9-16
            "17, 10",   // začátek segmentu 17-22
            "22, 20",   // konec segmentu 17-22
            "23, 23",   // začátek segmentu 23-28
            "28, 38",   // konec segmentu 23-28 - poslední úroveň JEŠTĚ bez EMinLev5
            "29, 42"    // první úroveň segmentu 29+ - EMinLev5 se poprvé uplatní
    })
    void ořezáváSegmentyNaSprávnýchHranicích(int level, int expected) {
        assertEquals(expected, Seg5.compute(level, E_MIN, E_MIN_LEV1, E_MIN_LEV2, E_MIN_LEV3, E_MIN_LEV4, E_MIN_LEV5));
    }

    /**
     * "Bez stropu" z PLAN_P1_SPRING_SLICE.md sekce 1.3/2.2: jediný segment
     * (EMinLev5), který horní {@code min(...)} ořez vůbec nemá. Test ověřuje
     * dvě věci najednou: (1) hodnota nad úrovní 28 dál lineárně roste -
     * rozdíl mezi dvěma libovolnými úrovněmi nad 28 je vždy
     * {@code (rozdíl úrovní) * EMinLev5}; (2) i pro extrémně vysokou úroveň
     * (200) se nic nezasekne na žádném stropu.
     */
    @ParameterizedTest(name = "seg5({0}) roste o EMinLev5 na úroveň nad 28")
    @CsvSource({
            "29, 30",
            "30, 31",
            "99, 100",
            "199, 200"
    })
    void rosteBezeStropuNadÚrovní28(int levelNiž, int levelVýš) {
        int rozdíl = Seg5.compute(levelVýš, E_MIN, E_MIN_LEV1, E_MIN_LEV2, E_MIN_LEV3, E_MIN_LEV4, E_MIN_LEV5)
                - Seg5.compute(levelNiž, E_MIN, E_MIN_LEV1, E_MIN_LEV2, E_MIN_LEV3, E_MIN_LEV4, E_MIN_LEV5);
        assertEquals(E_MIN_LEV5, rozdíl);
    }
}
