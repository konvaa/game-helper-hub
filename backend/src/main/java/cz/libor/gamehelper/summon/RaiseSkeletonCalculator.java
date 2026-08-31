package cz.libor.gamehelper.summon;

/**
 * Jádro celého P1 slice: pro danou úroveň Raise Skeleton, úroveň Skeleton
 * Mastery a bojové staty monstra (necroskeleton) spočítá staty jednoho
 * vyvolaného kostlivce. Čistá funkce - PLAN_P1_SPRING_SLICE.md sekce 7.1:
 * "žádné repozitáře, žádné anotace, žádný kontext". {@code SummonService}
 * (dál v tomhle kroku) je jediné místo, které z databáze načte {@code Skill}
 * "raise_skeleton" a odpovídající {@code MonsterStat}, sestaví z nich
 * {@link RaiseSkeletonInput} a zavolá {@link #compute}.
 *
 * <p><b>Odkud vzorec je:</b> D2R vzorec pro Raise Skeleton NENÍ nikde
 * exportovaný jako hotové číslo - Python reference
 * ({@code scripts/summon_engine}) ho vyhodnocuje obecným interpretem
 * herních výrazů (ten, co v P1 slice záměrně NEPORTUJEME - viz
 * {@code docs/BACKEND_ZAPISNIK.md}, "co je mimo scope"). Pro JEDEN
 * konkrétní skill (Raise Skeleton) šlo vzorec ručně rozepsat do uzavřeného
 * tvaru - PLAN_P1_SPRING_SLICE.md sekce 1.3 ho odvozuje a rovnou ověřuje
 * proti referenčnímu případu {@code rs20_sm11}. Tahle třída je ten uzavřený
 * tvar přepsaný do Javy, ZNOVU nezávisle ověřený (viz níž) proti všem
 * 8 baseline případům, ne jen tomu jednomu z plánu.
 *
 * <p><b>Čtyři konstanty {@code Param2..Param5}</b> nejsou sloupce v DB -
 * {@code SKILLS_PROJECTION.md} je do projekce {@code Skill} entity
 * záměrně NEZAHRNULA (322 → 26 sloupců, jen to, co tenhle jeden slice
 * potřebuje). Jsou to efektové koeficienty specifické pro skill Raise
 * Skeleton (%HP/úroveň, %dmg/úroveň, AR/úroveň, DEF/úroveň) - hodnoty
 * ověřené přímo v {@code data/.../skills_raw.json} (řádek "Raise Skeleton",
 * pole {@code Param2}=50, {@code Param3}=7, {@code Param4}=15,
 * {@code Param5}=15) a zapsané sem naostro, přesně jako
 * {@code docs/PLAN_P1_SPRING_SLICE.md} v poznámce k rozsahu předpokládá
 * ("Raise Skeleton formula hardcoded"). Kdyby projekt měl počítat i jiné
 * summony, tahle čtyři čísla by se musela stát vstupem (další sloupce
 * projekce) - pro JEDEN skill by to byla zbytečná obecnost navíc.
 *
 * <p><b>Odkud vzorce (nezávisle ověřeno, ne opsáno z plánu):</b> než jsem
 * napsal tenhle soubor, přepočítal jsem všech 8 baseline případů
 * v Pythonu přímo proti {@code monsters_base.json} (necroskeleton/hell:
 * {@code max_hp=42, a1_min_d=1, a1_max_d=2, ac=6, a1_th=6}) a
 * {@code skills_raw.json} (Raise Skeleton: {@code EMin=0},
 * {@code EMinLev1..5 = 0,1,2,3,4}) - všech 8 × 8 hodnot (6 finálních +
 * 2 extract) sedí přesně. Podrobnosti a přesný postup jsou v
 * {@code docs/BACKEND_ZAPISNIK.md}, "Krok 6".
 *
 * <ul>
 *   <li>{@code rsInternalFlat = seg5(rsLevel)} - čistě z koeficientů
 *       skillu, Skeleton Mastery do něj NEVSTUPUJE.</li>
 *   <li>{@code totalSkillBonus = rsInternalFlat + smLevel * 2} - "+2 za
 *       úroveň Skeleton Mastery" je další hardcoded konstanta stejné
 *       povahy jako Param2..5 výš (ne DB sloupec, ověřeno proti baseline).</li>
 *   <li>{@code hp} a {@code phys_min/max} jediné dělí procenty (dělení
 *       100.0), proto jediné potřebují {@link Math#floor}; {@code defense}
 *       a {@code attackRating} jsou čistě celočíselné (žádné dělení) -
 *       {@code Math.floor} by tam byl jen kosmetický, proto tam není.</li>
 *   <li>{@code count} počítá podle stejného vzorce, jaký je v
 *       {@code skills_raw.json} jako text ({@code petmax_expr},
 *       {@code "(lvl < 4) ?lvl:(2+lvl/3)"}) - PLAN_P1_SPRING_SLICE.md 1.3
 *       ho zapisuje naostro (obecný interpret výrazů je mimo scope).</li>
 * </ul>
 *
 * <p><b>[Python → Java]</b> Python reference tenhle vzorec nemá vypsaný
 * takhle explicitně - vyhodnocuje ho interpretem nad syrovým textovým
 * výrazem ze hry. Tahle třída dělá přesně opačnou věc: bere JEDEN, ručně
 * vytěžený a ověřený speciální případ toho obecného mechanismu a zapisuje
 * ho jako normální, staticky typovaný Java kód - rychlejší a čitelnější pro
 * tenhle jeden skill, ale bez schopnosti Python enginu spočítat cokoli
 * jiného bez další ruční práce. Je to vědomý kompromis pro P1 (jeden skill,
 * jeden slice), ne obecné řešení.
 *
 * <p>Package-private - volá ji jen {@code SummonService} a
 * {@code RaiseSkeletonCalculatorTest}, oba ve stejném balíčku
 * {@code cz.libor.gamehelper.summon} (viz {@link RaiseSkeletonResult}
 * javadoc pro kontrast s tím, co veřejné BÝT musí).
 */
final class RaiseSkeletonCalculator {

    /** Param2 skillu Raise Skeleton ({@code skills_raw.json}) - "% HP monstra navíc za úroveň nad 3". */
    private static final int HP_PERCENT_PER_LEVEL = 50;

    /** Param3 - "% fyzického poškození navíc za úroveň nad 3". */
    private static final int DAMAGE_PERCENT_PER_LEVEL = 7;

    /** Param4 - "AR navíc za (úroveň skillu + úroveň Skeleton Mastery)", čistě aditivní, bez procent. */
    private static final int ATTACK_RATING_PER_LEVEL = 15;

    /** Param5 - "obrana navíc za (úroveň skillu + úroveň Skeleton Mastery)", čistě aditivní. */
    private static final int DEFENSE_PER_LEVEL = 15;

    /** Kolik seg5-flat přidá KAŽDÁ úroveň Skeleton Mastery (ne DB sloupec - viz javadoc třídy). */
    private static final int SKELETON_MASTERY_FLAT_PER_LEVEL = 2;

    /** Kolik HP přidá KAŽDÁ úroveň Skeleton Mastery, jako přímý flat příspěvek (ne přes flat/procenta). */
    private static final int SKELETON_MASTERY_HP_PER_LEVEL = 8;

    /**
     * Od jaké úrovně skillu začínají procentuální bonusy (HP%, dmg%) růst -
     * úrovně 1-3 jsou "základ", 4. úroveň je první, co dostane +1*Param%.
     * Odtud {@code (rsLevel - 3)} ve všech procentuálních výrazech níž.
     */
    private static final int PERCENT_SCALING_BASE_LEVEL = 3;

    private RaiseSkeletonCalculator() {
        // Utility třída - jen statická metoda, instance nedávají smysl.
    }

    static RaiseSkeletonResult compute(RaiseSkeletonInput input) {
        int rsLevel = input.raiseSkeletonLevel();
        int smLevel = input.skeletonMasteryLevel();
        RaiseSkeletonInput.SkillScaling scaling = input.scaling();
        RaiseSkeletonInput.MonsterCombatStats monster = input.monsterStats();

        int rsInternalFlat = Seg5.compute(rsLevel,
                scaling.eMin(), scaling.eMinLev1(), scaling.eMinLev2(),
                scaling.eMinLev3(), scaling.eMinLev4(), scaling.eMinLev5());
        int totalSkillBonus = rsInternalFlat + smLevel * SKELETON_MASTERY_FLAT_PER_LEVEL;

        // Pod 4. úrovní procentuální bonusy NEROSTOU - drží se na nule, nesnižují se.
        // Bez toho clampu vyjde na rsLevel 1-2 záporný počet úrovní (1-3 = -2), z něj
        // záporné dmg% i HP%, a výsledek je NIŽŠÍ než holé staty monstra.
        // Python reference to řeší strážcem "0.0 if rs_lvl < 4 else (rs_lvl - 3) * par"
        // na obou místech zvlášť (raise_skeleton.py, řádky 48 a 129); tady stačí jednou,
        // protože damageMultiplier i hp z téhle proměnné oba vycházejí.
        // Pro rsLevel == 3 dává obojí shodně 0, takže je to přesná ekvivalence.
        int levelsAbovePercentBase = Math.max(0, rsLevel - PERCENT_SCALING_BASE_LEVEL);
        double damageMultiplier = 1.0 + (levelsAbovePercentBase * DAMAGE_PERCENT_PER_LEVEL) / 100.0;

        int hp = (int) Math.floor(
                monster.maxHp()
                        + (monster.maxHp() * (double) (levelsAbovePercentBase * HP_PERCENT_PER_LEVEL)) / 100.0
                        + smLevel * SKELETON_MASTERY_HP_PER_LEVEL);

        int physMin = (int) Math.floor((monster.a1MinD() + totalSkillBonus) * damageMultiplier);
        int physMax = (int) Math.floor((monster.a1MaxD() + totalSkillBonus) * damageMultiplier);

        // Čistě celočíselné - žádné dělení, žádná potřeba floor().
        int defense = monster.ac() + (rsLevel + smLevel) * DEFENSE_PER_LEVEL;
        int attackRating = monster.a1Th() + (rsLevel + smLevel) * ATTACK_RATING_PER_LEVEL;

        // skills_raw.json, petmax_expr: "(lvl < 4) ?lvl:(2+lvl/3)".
        int count = rsLevel < 4 ? rsLevel : (int) Math.floor(2 + rsLevel / 3.0);

        return new RaiseSkeletonResult(hp, physMin, physMax, defense, attackRating, count,
                rsInternalFlat, totalSkillBonus);
    }
}
