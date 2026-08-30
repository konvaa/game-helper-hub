package cz.libor.gamehelper.monster.dto;

import cz.libor.gamehelper.monster.Monster;
import cz.libor.gamehelper.monster.MonsterStat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Tvar pro {@code GET /api/monsters/{id}}. Proč DTO, a ne entita přímo -
 * viz {@link cz.libor.gamehelper.skill.dto.SkillSummaryDto}, stejný
 * princip platí tady beze změny.
 *
 * <p>Skládá {@link Monster} (identita) a VŠECHNY tři {@link MonsterStat}
 * řádky (normal/nightmare/hell) do jedné odpovědi - přesně to, co
 * {@code MonsterStatRepository.findByMonsterId} (krok 4) vrací, jen
 * seřazené HERNĚ, ne podle pořadí v databázi. Tohle je přesně to místo,
 * které javadoc {@code MonsterStatRepository} slíbil v kroku 4: "volající
 * (servisní vrstva v kroku 5/6) seřadí... podle {@code Difficulty.ordinal()}".
 */
public record MonsterDto(Long id, String monsterKey, String nameStr, List<DifficultyStatsDto> stats) {

    /**
     * Jeden řádek {@code monster_stat}. {@code difficulty} je STRING
     * ("normal"/"nightmare"/"hell"), ne Javí enum {@code Difficulty} -
     * DTO je hranice API a JSON klient nemá důvod znát Javí typ
     * {@code Difficulty}, jen jeho textovou hodnotu (stejný důvod jako
     * {@link cz.libor.gamehelper.monster.DifficultyConverter} pro databázi
     * - "malými písmeny, shoda s konvencí projektu", jen na druhé straně
     * aplikace).
     */
    public record DifficultyStatsDto(
            String difficulty,
            Short level,
            Integer minHp,
            Integer maxHp,
            Integer ac,
            Integer a1MinD,
            Integer a1MaxD,
            Integer a1Th,
            Integer a2MinD,
            Integer a2MaxD,
            Integer a2Th,
            Integer s1MinD,
            Integer s1MaxD,
            Integer s1Th) {

        static DifficultyStatsDto from(MonsterStat stat) {
            return new DifficultyStatsDto(
                    stat.getDifficulty().name().toLowerCase(Locale.ROOT),
                    stat.getLevel(),
                    stat.getMinHp(),
                    stat.getMaxHp(),
                    stat.getAc(),
                    stat.getA1MinD(),
                    stat.getA1MaxD(),
                    stat.getA1Th(),
                    stat.getA2MinD(),
                    stat.getA2MaxD(),
                    stat.getA2Th(),
                    stat.getS1MinD(),
                    stat.getS1MaxD(),
                    stat.getS1Th());
        }
    }

    public static MonsterDto from(Monster monster, List<MonsterStat> stats) {
        List<DifficultyStatsDto> sortedStats = stats.stream()
                // Herní pořadí (normal, nightmare, hell) = pořadí deklarace
                // konstant v Difficulty enumu (krok 2), NE abecední pořadí
                // textového sloupce difficulty - viz MonsterStatRepository
                // javadoc (krok 4) pro celé zdůvodnění.
                .sorted(Comparator.comparingInt(stat -> stat.getDifficulty().ordinal()))
                .map(DifficultyStatsDto::from)
                .toList();
        return new MonsterDto(monster.getId(), monster.getMonsterKey(), monster.getNameStr(), sortedStats);
    }
}
