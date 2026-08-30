package cz.libor.gamehelper.summon;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Jádro celého P1 slice (Libořina formulace) - parametrizovaný test nad
 * {@code scripts/tests/baseline_raise_skeleton.json}, PLAN_P1_SPRING_SLICE.md
 * sekce 7.2. Až tenhle test projde 8/8, je to důkaz, že
 * {@link RaiseSkeletonCalculator} dává STEJNÁ čísla jako referenční Python
 * implementace pro všech 8 baseline případů - to je věta, kterou bude tvrdit
 * README ("8/8 shodných výsledků").
 *
 * <h2>Co přesně se porovnává, s jakou tolerancí, a co se stane při neshodě</h2>
 *
 * <p><b>Kterých 8 hodnot na případ (64 assertions celkem):</b> šest
 * "finálních" čísel z {@code expected.final} ({@code hp}, {@code phys_min},
 * {@code phys_max}, {@code defense}, {@code attack_rating}, {@code count})
 * a dvě "extract" mezihodnoty z {@code expected.extract}
 * ({@code rs_internal_flat}, {@code total_skill_bonus}) - přesně sada, kterou
 * PLAN_P1_SPRING_SLICE.md 7.2 předepisuje.
 *
 * <p><b>Tolerance:</b> ŽÁDNÁ - všech 8 hodnot se porovnává na PŘESNOU shodu,
 * ne s epsilonem.
 * <ul>
 *   <li>Šest finálních hodnot jsou {@code int} na obou stranách (baseline
 *       JSON je zapisuje jako celá čísla, kalkulátor je vrací jako
 *       {@code int} po {@link Math#floor}) - {@code assertEquals(int, int)}
 *       je proto triviálně přesná shoda, tolerance tu ani nedává smysl.</li>
 *   <li>{@code rs_internal_flat}/{@code total_skill_bonus} baseline JSON
 *       zapisuje jako desetinná čísla (16.0, 38.0, ...), ale
 *       {@link RaiseSkeletonResult} je počítá čistě celočíselně (viz jeho
 *       javadoc) - matematicky jsou VŽDY celá čísla. Test proto použije
 *       {@code assertEquals(double, double, delta)} s {@code delta = 0.0}:
 *       to NENÍ tolerance v obvyklém smyslu ("povol malou odchylku"), je to
 *       jen technická nutnost přetížené metody (baseline hodnota je na
 *       vstupu {@code double}) - žádná odchylka přijata není.</li>
 * </ul>
 * Pro čísla, kde se opravdu dělí procenty ({@code hp}, {@code phys_min/max}),
 * je bezpečnost přesné shody bez zaokrouhlovací tolerance ověřená napřímo -
 * viz {@code docs/BACKEND_ZAPISNIK.md}, "Krok 6": pro všech 8 baseline
 * případů je desetinná část mezivýsledku PŘED {@code floor()} vzdálená od
 * celočíselné hranice nejméně o 0.01, což je o mnoho řádů víc, než jaká
 * chyba může vzniknout z {@code double} aritmetiky (~1e-15) - žádný z
 * 8 případů tedy neriskuje, že by se kvůli zaokrouhlovací chybě
 * {@code floor()} "přehoupl" na sousední celé číslo.
 *
 * <p><b>Co se stane při neshodě:</b> každý test případ volá
 * {@link org.junit.jupiter.api.Assertions#assertAll(String, org.junit.jupiter.api.function.Executable...)}
 * se všemi 8 assertions najednou. {@code assertAll} - na rozdíl od obyčejné
 * sekvence {@code assertEquals} volání - NEZASTAVÍ se na první nepovedené
 * assertion: spustí VŠECHNY, posbírá VŠECHNY selhání a vyhodí jednu
 * {@code MultipleFailuresError}, která je všechny vypíše najednou, každou
 * s vlastní zprávou (jméno pole - "hp", "phys_min", ...). Pro debugging je
 * to zásadní rozdíl: kdyby u jednoho případu selhaly třeba {@code hp} i
 * {@code defense} zároveň, uvidíš OBĚ chyby v jednom běhu, ne jen první a
 * pak muset opravovat a spouštět znovu, aby se objevila druhá. Navíc každý
 * test případ běží jako SAMOSTATNÝ parametrizovaný test (JUnit 5 je
 * nezávisle reportuje, viz {@code name} v {@code @ParameterizedTest}) - i
 * kdyby jeden ze 8 případů spadl, zbylých 7 se spustí a nahlásí zvlášť.
 *
 * <p><b>[Python → Java]</b> V Pythonu (pytest) by tohle byl
 * {@code @pytest.mark.parametrize} nad seznamem případů a {@code assert}
 * uvnitř by se zastavil na první nepovedené podmínce, pokud bys je neskládal
 * ručně do {@code pytest.approx}/vlastního porovnávače. JUnit 5
 * {@code assertAll} dělá tohle sbírání selhání ZABUDOVANĚ - není potřeba
 * žádná knihovna navíc.
 */
class RaiseSkeletonCalculatorTest {

    /**
     * Staty skillu Raise Skeleton (seg5 koeficienty) - ověřeno proti
     * {@code data/.../skills_raw.json} (řádek "Raise Skeleton"). Test je
     * záměrně nedostává z databáze (kalkulátor je bezstavová čistá funkce,
     * viz {@code RaiseSkeletonCalculator} javadoc) - DB-vázané načtení
     * stejných čísel přes {@code SkillRepository} ověřuje jiný test
     * ({@code SummonControllerTest} / {@code SkillRepositoryIT}), ne tenhle.
     */
    private static final RaiseSkeletonInput.SkillScaling RAISE_SKELETON_SCALING =
            new RaiseSkeletonInput.SkillScaling(0, 0, 1, 2, 3, 4);

    /**
     * Bojové staty {@code necroskeleton} na obtížnosti "hell" - ověřeno
     * proti {@code data/.../monsters_base.json}. Všech 8 baseline případů
     * je na "hell" (viz {@code meta}/{@code difficulty} v baseline JSON),
     * proto je tu jen jedna konstantní sada statů, ne mapa podle obtížnosti.
     */
    private static final RaiseSkeletonInput.MonsterCombatStats NECROSKELETON_HELL =
            new RaiseSkeletonInput.MonsterCombatStats(42, 1, 2, 6, 6);

    @ParameterizedTest(name = "{0} (rs={1}, sm={2})")
    @MethodSource("baselineCases")
    void odpovídáBaselineZReferenčníhoPythonEnginu(
            String id, int rsLevel, int smLevel, FinalValues expectedFinal, ExtractValues expectedExtract) {

        RaiseSkeletonInput input = new RaiseSkeletonInput(rsLevel, smLevel, RAISE_SKELETON_SCALING, NECROSKELETON_HELL);
        RaiseSkeletonResult result = RaiseSkeletonCalculator.compute(input);

        assertAll(id,
                () -> assertEquals(expectedFinal.hp(), result.hp(), "hp"),
                () -> assertEquals(expectedFinal.physMin(), result.physMin(), "phys_min"),
                () -> assertEquals(expectedFinal.physMax(), result.physMax(), "phys_max"),
                () -> assertEquals(expectedFinal.defense(), result.defense(), "defense"),
                () -> assertEquals(expectedFinal.attackRating(), result.attackRating(), "attack_rating"),
                () -> assertEquals(expectedFinal.count(), result.count(), "count"),
                () -> assertEquals(expectedExtract.rsInternalFlat(), (double) result.rsInternalFlat(), 0.0, "rs_internal_flat"),
                () -> assertEquals(expectedExtract.totalSkillBonus(), (double) result.totalSkillBonus(), 0.0, "total_skill_bonus")
        );
    }

    /**
     * Zdroj dat pro {@code @MethodSource} - přečte baseline JSON z
     * classpath (tam ho při buildu zkopíruje {@code maven-resources-plugin},
     * viz {@code pom.xml} a {@code docs/BACKEND_ZAPISNIK.md}, "Krok 6") a
     * převede jeho 8 případů na argumenty testu.
     *
     * <p>Soubor se čte z {@code /baseline_raise_skeleton.json} (kořen
     * classpath, ne podadresář) - {@code maven-resources-plugin} ho tam
     * kopíruje BEZ {@code targetPath}, takže skončí přímo v
     * {@code target/test-classes/baseline_raise_skeleton.json}.
     *
     * <p><b>{@code meta} blok (proč je namodelovaný, ne jen ignorovaný):</b>
     * baseline soubor má na top-level {@code meta} i {@code cases}. Prvně
     * jsem {@link BaselineFile} napsal jen s {@code cases} - Jackson má
     * defaultně {@code FAIL_ON_UNKNOWN_PROPERTIES=true}, takže na neznámé
     * pole {@code meta} spadl s {@code UnrecognizedPropertyException} a test
     * se vůbec nespustil (0 argumentů pro {@code @ParameterizedTest}, viz
     * {@code docs/BACKEND_ZAPISNIK.md}, "Krok 6" - oprava). Dvě možné opravy:
     * (a) {@code @JsonIgnoreProperties(ignoreUnknown = true)} na
     * {@link BaselineFile} - Jackson by {@code meta} tiše přeskočil; (b)
     * {@code meta} taky namodelovat. Zvolil jsem (b), ze dvou důvodů:
     * <ul>
     *   <li>{@code meta} nese informaci, která se hodí vědět, PROTI ČEMU se
     *       vlastně porovnává - {@code schema} (verze tvaru baseline
     *       souboru) a {@code skill} (že jde skutečně o "raise_skeleton",
     *       ne omylem o jiný/starší export) - obojí se dá teď při běhu testu
     *       ověřit, ne jen důvěřovat názvu souboru.</li>
     *   <li>Plošné {@code ignoreUnknown = true} by sice opravilo TENHLE pád,
     *       ale zároveň by navždycky ztlumilo signál pro JAKÉKOLI budoucí
     *       neznámé pole v souboru - kdyby se tvar baseline souboru časem
     *       změnil jinak, než tenhle test čeká, chci o tom vědět hlasitým
     *       selháním deserializace, ne tichým zahozením pole. Explicitní
     *       {@link Meta} record drží FAIL_ON_UNKNOWN_PROPERTIES zapnuté pro
     *       cokoli DALŠÍHO neočekávaného - je to užší, ale bezpečnější oprava
     *       než blanket-ignore.</li>
     * </ul>
     * {@code meta} se vypíše na {@code System.out} (skončí v
     * {@code target/surefire-reports/...RaiseSkeletonCalculatorTest.txt},
     * přesně to "aspoň do logu testu", o co Libor žádal) a {@code skill}
     * pole se navíc ověří {@code assertEquals} - kdyby se do
     * {@code target/test-classes/baseline_raise_skeleton.json} omylem
     * zkopíroval jiný soubor (např. {@code baseline_raise_skeleton_highlvl.json}),
     * test by na tom spadl SROZUMITELNOU hláškou hned na začátku, ne až na
     * matoucích rozdílech v číslech u jednotlivých případů.
     */
    private static Stream<Arguments> baselineCases() throws IOException {
        try (InputStream stream = RaiseSkeletonCalculatorTest.class.getResourceAsStream("/baseline_raise_skeleton.json")) {
            assertNotNull(stream,
                    "baseline_raise_skeleton.json nenalezen na classpath - zkontroluj "
                            + "maven-resources-plugin v pom.xml (kopíruje z ../scripts/tests/) "
                            + "a že soubor v scripts/tests/ v repu existuje");

            BaselineFile baseline = new ObjectMapper().readValue(stream, BaselineFile.class);

            Meta meta = baseline.meta();
            System.out.printf(
                    "[RaiseSkeletonCalculatorTest] baseline meta: schema=%s skill=%s generated_dir=%s notes=%s%n",
                    meta.schema(), meta.skill(), meta.generatedDir(), meta.notes());
            assertEquals("raise_skeleton", meta.skill(),
                    "baseline_raise_skeleton.json meta.skill neodpovídá 'raise_skeleton' - "
                            + "zkopíroval se do target/test-classes/ opravdu ten správný soubor?");

            return baseline.cases().stream()
                    .map(c -> Arguments.of(c.id(), c.rs(), c.sm(), c.expected().finalValues(), c.expected().extract()));
        }
    }

    // --- Jackson mapování tvaru scripts/tests/baseline_raise_skeleton.json ---
    // Recordy jen pro deserializaci téhle jedné testovací fixture - záměrně
    // oddělené od RaiseSkeletonResult/RaiseSkeletonInput výš (ty jsou tvar
    // API domény, tyhle jsou tvar JSON souboru; kdyby se JSON tvar baseline
    // souboru někdy změnil, mění se jen tahle dvojice, ne produkční kód).

    private record BaselineFile(Meta meta, List<Case> cases) {
    }

    /**
     * Top-level {@code meta} blok baseline souboru - viz
     * {@link #baselineCases()} javadoc, proč je namodelovaný a ne jen
     * ignorovaný. {@code generatedDir} mapuje na JSON {@code generated_dir}
     * (Python konvence podtržítka, stejná past jako u ostatních recordů
     * níž - proto {@code @JsonProperty}).
     */
    private record Meta(String schema, String skill, @JsonProperty("generated_dir") String generatedDir, String notes) {
    }

    private record Case(String id, int rs, int sm, String difficulty, Expected expected) {
    }

    private record Expected(@JsonProperty("final") FinalValues finalValues, ExtractValues extract) {
    }

    private record FinalValues(
            int hp,
            @JsonProperty("phys_min") int physMin,
            @JsonProperty("phys_max") int physMax,
            int defense,
            @JsonProperty("attack_rating") int attackRating,
            int count) {
    }

    private record ExtractValues(
            @JsonProperty("rs_internal_flat") double rsInternalFlat,
            @JsonProperty("total_skill_bonus") double totalSkillBonus) {
    }
}
