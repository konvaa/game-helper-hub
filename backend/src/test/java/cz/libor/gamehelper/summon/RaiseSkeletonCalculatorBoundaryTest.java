// Doplňující unit testy RaiseSkeletonCalculatoru: hraniční úrovně skillu
// (1-4, 99), počet kostlivců a vliv Skeleton Mastery (synergie) izolovaně.
// Samostatná třída, ne rozšíření RaiseSkeletonCalculatorTest: ten je
// jednoúčelový (8/8 shoda s ověřenou baseline) a jeho tvrzení "8/8" v README
// má zůstat pravdivé a dohledatelné k jednomu souboru. Tady jsou pravidla
// vzorce a regresní shoda s Python referencí, ne porovnání se hrou.
//
// [Python → Java] @CsvSource/@ValueSource jsou obdoba @pytest.mark.parametrize;
// JUnit převede textové hodnoty z CSV na typy parametrů metody (int, String) sám.

package cz.libor.gamehelper.summon;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RaiseSkeletonCalculatorBoundaryTest {

    // Stejné vstupy jako RaiseSkeletonCalculatorTest (necroskeleton/hell,
    // koeficienty seg5 skillu Raise Skeleton). Konstanty tam jsou private,
    // proto kopie - raději dvě stejné konstanty než měnit existující test.
    private static final RaiseSkeletonInput.SkillScaling RAISE_SKELETON_SCALING =
            new RaiseSkeletonInput.SkillScaling(0, 0, 1, 2, 3, 4);

    private static final RaiseSkeletonInput.MonsterCombatStats NECROSKELETON_HELL =
            new RaiseSkeletonInput.MonsterCombatStats(42, 1, 2, 6, 6);

    private static RaiseSkeletonResult compute(int rsLevel, int smLevel) {
        return RaiseSkeletonCalculator.compute(
                new RaiseSkeletonInput(rsLevel, smLevel, RAISE_SKELETON_SCALING, NECROSKELETON_HELL));
    }

    /**
     * Případy z baseline sad, které Java dosud netestovala:
     * {@code baseline_raise_skeleton_lowlvl.json} (úrovně 1-3, větev
     * {@code lvl < 4}, kvůli které vznikl commit a522990) a hell případy
     * z {@code baseline_raise_skeleton_highlvl.json} (úrovně nad 28, SM=0,
     * SM > RS).
     *
     * <p>Čísla jsou do {@code @CsvSource} opsaná, ne čtená ze souboru: pom.xml
     * kopíruje na classpath jen {@code baseline_raise_skeleton.json} a jeho
     * rozšíření by byla změna existující konfigurace, která pro tenhle test
     * není nezbytná. Cena: při přegenerování baseline se musí přepsat i tady.
     *
     * <p>POZOR na rozdílnou váhu: lowlvl je výstup Python enginu, highlvl je
     * podle {@code docs/KNOWN_ACCURACY_GAPS.md} "neověřeno proti hře". Test
     * tedy tvrdí "Java == Python reference", ne "Java == hra".
     *
     * <p>Nightmare/normal případy z highlvl tu chybí záměrně: staty
     * necroskeletona pro tyto obtížnosti v repozitáři nejsou (reálný
     * monsters_base.json je mimo git) a zpětně je dopočítat z očekávaných
     * výsledků by udělalo test kruhovým.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // id,              rs, sm,   hp, min, max,  def,   ar, count, flat, bonus
            "rs01_sm01_hell,     1,  1,   50,   3,   4,   36,   36,  1,    0,    2",
            "rs02_sm01_hell,     2,  1,   50,   3,   4,   51,   51,  2,    0,    2",
            "rs03_sm01_hell,     3,  1,   50,   3,   4,   66,   66,  3,    0,    2",
            "rs23_sm20_hell,    23, 20,  622, 153, 156,  651,  651,  9,   23,   63",
            "rs25_sm25_hell,    25, 25,  704, 203, 205,  756,  756, 10,   29,   79",
            "rs28_sm28_hell,    28, 28,  791, 261, 264,  846,  846, 11,   38,   94",
            "rs29_sm29_hell,    29, 29,  820, 284, 287,  876,  876, 11,   42,  100",
            "rs35_sm35_hell,    35, 35,  994, 443, 447, 1056, 1056, 13,   66,  136",
            "rs40_sm40_hell,    40, 40, 1139, 599, 603, 1206, 1206, 15,   86,  166",
            "rs45_sm20_hell,    45, 20, 1084, 579, 583,  981,  981, 17,  106,  146",
            "rs20_sm40_hell,    20, 40,  719, 212, 214,  906,  906,  8,   16,   96",
            "rs40_sm00_hell,    40,  0,  819, 312, 315,  606,  606, 15,   86,   86"
    })
    void odpovídáLowlvlAHighlvlBaseline(String id, int rs, int sm,
                                         int hp, int physMin, int physMax, int defense, int attackRating,
                                         int count, int rsInternalFlat, int totalSkillBonus) {
        RaiseSkeletonResult result = compute(rs, sm);

        // assertAll ze stejného důvodu jako v RaiseSkeletonCalculatorTest:
        // při neshodě chci vidět všechna rozbitá pole najednou.
        assertAll(id,
                () -> assertEquals(hp, result.hp(), "hp"),
                () -> assertEquals(physMin, result.physMin(), "phys_min"),
                () -> assertEquals(physMax, result.physMax(), "phys_max"),
                () -> assertEquals(defense, result.defense(), "defense"),
                () -> assertEquals(attackRating, result.attackRating(), "attack_rating"),
                () -> assertEquals(count, result.count(), "count"),
                () -> assertEquals(rsInternalFlat, result.rsInternalFlat(), "rs_internal_flat"),
                () -> assertEquals(totalSkillBonus, result.totalSkillBonus(), "total_skill_bonus"));
    }

    /**
     * Úrovně 1-3 nesmí dostat žádný procentuální bonus - HP i damage jsou
     * na všech třech stejné. Kdyby chyběl clamp {@code Math.max(0, rs - 3)},
     * vyšlo by na rs=1 záporné procento a HP pod základem monstra.
     */
    @Test
    void úrovně1až3MajíStejnéHpADamage() {
        RaiseSkeletonResult rs1 = compute(1, 1);
        RaiseSkeletonResult rs2 = compute(2, 1);
        RaiseSkeletonResult rs3 = compute(3, 1);

        assertAll(
                () -> assertEquals(rs3.hp(), rs1.hp(), "hp rs1 vs rs3"),
                () -> assertEquals(rs3.hp(), rs2.hp(), "hp rs2 vs rs3"),
                () -> assertEquals(rs3.physMin(), rs1.physMin(), "phys_min rs1 vs rs3"),
                () -> assertEquals(rs3.physMax(), rs1.physMax(), "phys_max rs1 vs rs3"));
    }

    /**
     * Úroveň 4 je první, kde procenta rostou: +50 % HP monstra (Param2)
     * a +7 % damage (Param3). Ručně: hp = 42 + 42*50/100 + 1*8 = 71;
     * phys_min = floor((1 + 2) * 1.07) = 3; phys_max = floor((2 + 2) * 1.07) = 4.
     * Damage se na rs=4 ještě nezmění jen kvůli floor() - proto test
     * ověřuje i hp, kde je skok vidět.
     */
    @Test
    void úroveň4JePrvníSProcentuálnímBonusem() {
        RaiseSkeletonResult rs4 = compute(4, 1);

        assertAll(
                () -> assertEquals(71, rs4.hp(), "hp"),
                () -> assertEquals(3, rs4.physMin(), "phys_min"),
                () -> assertEquals(4, rs4.physMax(), "phys_max"));
    }

    /**
     * {@code petmax_expr = "(lvl < 4) ?lvl:(2+lvl/3)"}. Hranice: do 3 roste
     * o 1 na úroveň, na 4 a 5 se zastaví na 3 (2 + 4/3 = 3), další kostlivec
     * až na 6. 99 je horní mez validace v ComputeRequest.
     */
    @ParameterizedTest(name = "rs={0} -> count={1}")
    @CsvSource({
            "1, 1",
            "3, 3",
            "4, 3",
            "5, 3",
            "6, 4",
            "20, 8",
            "99, 35"
    })
    void početKostlivcůNaHranicíchVzorce(int rs, int expectedCount) {
        assertEquals(expectedCount, compute(rs, 1).count());
    }

    /**
     * Skeleton Mastery izolovaně: +1 úroveň SM při stejném RS musí přidat
     * přesně +8 HP, +15 DEF, +15 AR a +2 do flat bonusu - a NESMÍ změnit
     * rsInternalFlat (ten je čistě z úrovně Raise Skeletonu). Testuje se
     * rozdílem dvou výpočtů, takže nezáleží na absolutních hodnotách.
     * rs=2 je schválně pod hranicí procent, rs=4 přesně na ní a rs=40
     * v segmentu bez stropu.
     */
    @ParameterizedTest(name = "rs={0}")
    @ValueSource(ints = {2, 4, 20, 40})
    void každáÚroveňSkeletonMasteryPřidáPevnýPříspěvek(int rs) {
        RaiseSkeletonResult nižší = compute(rs, 10);
        RaiseSkeletonResult vyšší = compute(rs, 11);

        assertAll("rs=" + rs,
                () -> assertEquals(8, vyšší.hp() - nižší.hp(), "hp za úroveň SM"),
                () -> assertEquals(15, vyšší.defense() - nižší.defense(), "defense za úroveň SM"),
                () -> assertEquals(15, vyšší.attackRating() - nižší.attackRating(), "AR za úroveň SM"),
                () -> assertEquals(2, vyšší.totalSkillBonus() - nižší.totalSkillBonus(), "flat bonus za úroveň SM"),
                () -> assertEquals(nižší.rsInternalFlat(), vyšší.rsInternalFlat(), "rsInternalFlat na SM nezávisí"));
    }

    /**
     * Počet kostlivců závisí jen na úrovni Raise Skeletonu, ne na Skeleton
     * Mastery - v datech je petmax výraz jen nad {@code lvl}.
     */
    @Test
    void početNezávisíNaSkeletonMastery() {
        assertEquals(compute(20, 0).count(), compute(20, 40).count());
    }
}
