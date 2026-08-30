package cz.libor.gamehelper.summon;

/**
 * Vstup do {@link RaiseSkeletonCalculator#compute}. "Prostý vstupní record"
 * z PLAN_P1_SPRING_SLICE.md sekce 7.1 - žádná {@code @Entity}, žádná JPA
 * anotace, žádný odkaz na repozitáře. Servisní vrstva ({@code SummonService},
 * krok 6 později v tomhle souboru) je jediné místo, které tenhle record
 * SESTAVUJE z databázových entit ({@code Skill}, {@code MonsterStat}) - sem
 * dovnitř žádná Spring/JPA závislost nesmí prosáknout, jinak by
 * {@link RaiseSkeletonCalculatorTest} nešel spustit bez Springu a databáze.
 *
 * <p>Dva vnořené recordy ({@link SkillScaling}, {@link MonsterCombatStats})
 * drží jen ta konkrétní čísla, která {@link RaiseSkeletonCalculator}
 * potřebuje - ne celé entity. Kalkulátor tak nezávisí na tvaru
 * {@code Skill}/{@code MonsterStat} tabulek, jen na těchhle malých DTO
 * vlastní domény (jiný smysl slova DTO než {@code skill.dto}/
 * {@code monster.dto} v kroku 5 - tam šlo o JSON tvar API, tady jde o vstup
 * čisté funkce).
 *
 * <p>Package-private, stejně jako {@link Seg5} - {@link SummonService} (jediné
 * místo, co ho sestavuje) i {@code RaiseSkeletonCalculatorTest} (jediné
 * místo, co ho čte přímo) jsou ve stejném balíčku
 * {@code cz.libor.gamehelper.summon}.
 */
record RaiseSkeletonInput(
        int raiseSkeletonLevel,
        int skeletonMasteryLevel,
        SkillScaling scaling,
        MonsterCombatStats monsterStats) {

    /**
     * Pětice {@code seg5} koeficientů skillu Raise Skeleton
     * ({@link cz.libor.gamehelper.skill.Skill#getEMin()} a
     * {@code getEMinLev1..5()}). Jména polí schválně kopírují jména
     * databázových/DTO polí (ne např. {@code base}/{@code step1}), ať je
     * v {@code SummonService} vidět na první pohled, které pole odkud jde -
     * viz i {@code SkillDetailDto.ScalingDto} v kroku 5, který řeší tu
     * samou pětici pro API response.
     */
    record SkillScaling(int eMin, int eMinLev1, int eMinLev2, int eMinLev3, int eMinLev4, int eMinLev5) {
    }

    /**
     * Bojové staty monstra PRO JEDNU KONKRÉTNÍ obtížnost (jeden řádek
     * {@code monster_stat}, už vybraný podle {@code difficulty} z requestu -
     * výběr správného řádku dělá {@code SummonService}, kalkulátor sám
     * o obtížnostech nic neví).
     */
    record MonsterCombatStats(int maxHp, int a1MinD, int a1MaxD, int ac, int a1Th) {
    }
}
