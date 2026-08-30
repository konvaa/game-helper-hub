package cz.libor.gamehelper.summon;

/**
 * Výstup {@link RaiseSkeletonCalculator#compute}. Osm hodnot - přesně těch
 * osm, co PLAN_P1_SPRING_SLICE.md sekce 7.2 předepisuje ověřit proti
 * baseline: šest "finálních" (to, co uvidí uživatel v UI) a dvě "extract"
 * mezivýsledky (to, co si Python reference ukládá pro debugging a co si
 * podle zadání krok 6 má ověřit i tenhle test).
 *
 * <p><b>Proč {@code rsInternalFlat}/{@code totalSkillBonus} jsou {@code int},
 * ne {@code double}:</b> obě hodnoty vznikají VÝHRADNĚ celočíselnou
 * aritmetikou ({@link Seg5#compute} + {@code skeletonMasteryLevel * 2}, viz
 * {@link RaiseSkeletonCalculator}) - žádné dělení, žádné {@code Math.floor}.
 * Matematicky jsou to celá čísla vždycky, bez výjimky. Baseline JSON
 * ({@code scripts/tests/baseline_raise_skeleton.json}) je serializuje jako
 * JSON čísla s desetinnou tečkou (16.0, 38.0, ...) - to je vlastnost
 * Python/JSON serializace, ne signál, že by hodnota mohla vyjít necelá.
 * Test to promítá do porovnání s deltou 0.0 (viz
 * {@link RaiseSkeletonCalculatorTest} javadoc) - je to STÁLE přesná shoda,
 * jen přes přetížení {@code assertEquals(double, double, double)}, protože
 * baseline hodnota na vstupu je {@code double}.
 *
 * <p><b>Proč {@code public}, na rozdíl od {@link RaiseSkeletonInput}
 * a {@link Seg5}:</b> stejná past, na kterou jsem narazil u DTO v kroku 5
 * ({@code SkillSummaryDto} javadoc) - {@code cz.libor.gamehelper.summon.dto}
 * je JINÝ balíček než {@code cz.libor.gamehelper.summon}, i když název
 * vypadá jako podbalíček. {@code ComputeResponse.from(...)} (v
 * {@code summon.dto}) bere {@code RaiseSkeletonResult} jako parametr a volá
 * jeho accessory ({@code result.hp()}, ...) - to přes balíčkovou hranici jde
 * jen s {@code public}. {@link RaiseSkeletonInput} a {@link Seg5} tuhle
 * hranici nikdy nepřekračují (zůstávají jen mezi {@code SummonService} a
 * testem, oba ve stejném balíčku), proto ony zůstaly package-private.
 */
public record RaiseSkeletonResult(
        int hp,
        int physMin,
        int physMax,
        int defense,
        int attackRating,
        int count,
        int rsInternalFlat,
        int totalSkillBonus) {
}
