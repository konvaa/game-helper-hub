package cz.libor.gamehelper.summon;

/**
 * Čistá implementace segmentového škálování ("seg5"), PLAN_P1_SPRING_SLICE.md
 * sekce 1.3 a 7.2. Žádný Spring, žádná JPA, žádný stav - jedna statická
 * metoda, jeden vzorec. Přesně to je smysl "krok 6 sekce 7.1": doména jako
 * čistá funkce, testovatelná v milisekundách bez databáze.
 *
 * <p><b>Co je seg5:</b> hodně skillů v D2 neroste s úrovní lineárně -
 * poroste jinak mezi levelem 2-8, jinak 9-16, jinak 17-22, jinak 23-28
 * a jinak (BEZ STROPU) od 29 výš. Hra to řeší pěti koeficienty
 * ({@code EMin}, {@code EMinLev1..5}) uloženými u KAŽDÉHO skillu zvlášť
 * (sloupce {@link cz.libor.gamehelper.skill.Skill#getEMin()} a
 * {@code getEMinLev1..5()}) - tahle třída je záměrně generická nad těmihle
 * koeficienty, ne "nadrátovaná" na konkrétní skill. Konkrétní čísla pro
 * Raise Skeleton (0, 0, 1, 2, 3, 4) dodává až volající kód
 * ({@link RaiseSkeletonCalculator}), tahle třída je nezná.
 *
 * <p><b>Proč "seg5" a ne třeba jen "scalingFor"</b>: pojmenování drží stejné
 * jako v PLAN_P1_SPRING_SLICE.md a v Python referenci - je to schválně
 * stejné slovo napříč dokumentací i kódem, ať se to při pohovoru i při
 * čtení kódu za měsíc dá dohledat jedním hledáním.
 *
 * <p>Vzorec (segment = interval úrovní, kde platí jeden koeficient):
 * <pre>
 * seg5(lvl) = EMin
 *           + min(7, max(0, lvl-1))  * EMinLev1     // segment 2-8
 *           + min(8, max(0, lvl-8))  * EMinLev2     // segment 9-16
 *           + min(6, max(0, lvl-16)) * EMinLev3     // segment 17-22
 *           + min(6, max(0, lvl-22)) * EMinLev4     // segment 23-28
 *           +         max(0, lvl-28) * EMinLev5     // segment 29+, bez stropu
 * </pre>
 * Každý {@code min(šířka_segmentu, max(0, lvl - dolní_hranice))} term říká
 * "kolik úrovní spadá do TOHOTO segmentu, a ne víc" - {@code max(0, ...)}
 * ořízne zdola (segment ještě nezačal), {@code min(šířka, ...)} ořízne
 * shora (segment už skončil, další level do něj nepřidává). Poslední
 * segment (EMinLev5) žádný horní {@code min} nemá - to je přesně to
 * "bez stropu", co PLAN_P1_SPRING_SLICE.md zdůrazňuje jako
 * nejdůležitější sloupec celé projekce.
 *
 * <p><b>[Python → Java]</b> Python referenční implementace
 * ({@code scripts/summon_engine}) tenhle vzorec počítá se stejnou
 * aritmetikou nad {@code int}. Tady je záměrně použitá jen celočíselná
 * aritmetika (žádné dělení, žádné {@code double}) - výsledek je proto VŽDY
 * přesné celé číslo, bez rizika, že by se objevila zaokrouhlovací chyba
 * jako u {@link RaiseSkeletonCalculator#compute}, kde se už dělí procenty.
 *
 * <p>Package-private (bez {@code public}): jediný volající je
 * {@link RaiseSkeletonCalculator} a jediný test je
 * {@code Seg5Test} - oba ve STEJNÉM balíčku
 * {@code cz.libor.gamehelper.summon} (test má stejné jméno balíčku, jen pod
 * {@code src/test/java} - Javu nezajímá, ze kterého source rootu package
 * pochází). Na rozdíl od DTO v kroku 5 ({@code skill.dto} je JINÝ balíček
 * než {@code skill} - viz {@code SkillSummaryDto} javadoc), tady žádná
 * balíčková hranice není, takže {@code public} by byl jen zbytečně širší
 * kontrakt, než je potřeba.
 */
final class Seg5 {

    private Seg5() {
        // Utility třída - jen statická metoda, instance nedávají smysl.
    }

    /**
     * @param level     efektivní úroveň skillu (NE strop investovaných bodů -
     *                  viz {@link cz.libor.gamehelper.skill.Skill#getMaxLevel()}
     *                  javadoc; sem smí přijít i číslo nad 20, seg5 sám
     *                  žádný strop nemá).
     * @param eMin      základ (segment "level 1").
     * @param eMinLev1  přírůstek na úroveň v segmentu 2-8 (šířka 7).
     * @param eMinLev2  přírůstek na úroveň v segmentu 9-16 (šířka 8).
     * @param eMinLev3  přírůstek na úroveň v segmentu 17-22 (šířka 6).
     * @param eMinLev4  přírůstek na úroveň v segmentu 23-28 (šířka 6).
     * @param eMinLev5  přírůstek na úroveň v segmentu 29+ (bez horní meze).
     * @return seg5(level), přesné celé číslo.
     */
    static int compute(int level,
                        int eMin,
                        int eMinLev1,
                        int eMinLev2,
                        int eMinLev3,
                        int eMinLev4,
                        int eMinLev5) {
        return eMin
                + segmentSpan(level, 1, 7) * eMinLev1
                + segmentSpan(level, 8, 8) * eMinLev2
                + segmentSpan(level, 16, 6) * eMinLev3
                + segmentSpan(level, 22, 6) * eMinLev4
                + Math.max(0, level - 28) * eMinLev5;
    }

    /**
     * Kolik úrovní z {@code level} spadá do segmentu, který začíná těsně
     * nad {@code lowerBound} a má nejvýš {@code width} úrovní.
     * Např. {@code segmentSpan(20, 8, 8)} = "kolik úrovní nad 8. úrovní se
     * do 20. úrovně vešlo, nejvýš 8" = min(8, max(0, 20-8)) = 8.
     */
    private static int segmentSpan(int level, int lowerBound, int width) {
        return Math.min(width, Math.max(0, level - lowerBound));
    }
}
