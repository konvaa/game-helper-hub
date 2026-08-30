package cz.libor.gamehelper.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ověřuje, že {@link NormalizeKey#normalize(String)} dává BIT-SHODNÝ výstup
 * s Python {@code normalize_key()} - viz javadoc {@link NormalizeKey}.
 *
 * <p>Očekávané hodnoty nejsou vymyšlené - jsou to skutečné výstupy Python
 * implementace spuštěné nad konkrétními vstupy (viz
 * docs/BACKEND_ZAPISNIK.md, krok 3). Čistý JUnit test, žádný Spring kontext
 * ani databáze - běží v milisekundách a je součástí "vždy zelené" sady
 * (viz {@code Seg5Test} a {@code RaiseSkeletonCalculatorTest} v kroku 6,
 * stejný princip).
 */
class NormalizeKeyTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            // Běžné názvy skillů - mezera na podtržítko, malá písmena.
            "Raise Skeleton,     raise_skeleton",
            "Skeletal Mage,      skeletal_mage",
            "Clay Golem,         clay_golem",

            // Golemí summony - PascalCase bez mezer. Skutecne pouzite jako
            // skill.summon (ClayGolem, BloodGolem, IronGolem, FireGolem);
            // monsters_base.json ma odpovidajici klic malymi pismeny.
            // Normalizace resi jen velikost pismen, zadna pomlcka/mezera.
            "ClayGolem,          claygolem",
            "BloodGolem,         bloodgolem",
            "IronGolem,          irongolem",
            "FireGolem,          firegolem",

            // 9 problemovych klicu z monsters_base.json (krok 2 zjisteni) -
            // pomlcka na podtrzitko, velke pismeno na male. Zadny skill na
            // ne dnes neodkazuje (overeno skriptem), ale monster.monster_key
            // se pocita normalize_key() stejne jako skill.monster_key, takze
            // musi vysledek sedet i pro tyhle vstupy.
            "trap-firebolt,      trap_firebolt",
            "trap-horzmissile,   trap_horzmissile",
            "Expansion,          expansion",
            "qual-kehk,          qual_kehk",

            // Uz normalizovany vstup projde beze zmeny.
            "necroskeleton,      necroskeleton",
            "already_normalized, already_normalized",

            // Vicenasobne mezery se slouci na jednu pred prevodem na
            // podtrzitko - jinak by vyslo "multiple__spaces" (dve podtrzitka).
            "'  multiple   spaces  ', multiple_spaces",

            // Kombinace pomlcky a mezery v jednom vstupu.
            "Mixed-Case Name,    mixed_case_name",
    })
    void normalizeMatchesPythonReferenceOutput(String raw, String expected) {
        assertThat(NormalizeKey.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"''", "'   '"})
    void blankInputNormalizesToEmptyString(String raw) {
        assertThat(NormalizeKey.normalize(raw)).isEmpty();
    }

    // @CsvSource neumi primo predat null jako hodnotu bunky bez zvlastniho
    // zapisu, a tenhle jeden pripad je srozumitelnejsi jako vlastni test
    // nez jako radek v tabulce.
    @org.junit.jupiter.api.Test
    void nullInputNormalizesToEmptyString() {
        assertThat(NormalizeKey.normalize(null)).isEmpty();
    }
}
