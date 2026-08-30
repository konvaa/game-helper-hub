package cz.libor.gamehelper.monster;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Staty jednoho monstra pro všechny obtížnosti najednou -
 * {@code GET /api/monsters/{id}} (krok 5) i {@code RaiseSkeletonCalculator}
 * (krok 6) potřebují všechny tři řádky (normal/nightmare/hell), ne jen
 * jeden.
 *
 * <p><b>{@code findByMonsterId} funguje, i když {@link MonsterStat} nemá
 * žádné pole {@code monsterId}</b> - má jen {@code @ManyToOne Monster
 * monster}. Spring Data umí odvozený název metody rozparsovat i PŘES
 * asociaci: {@code MonsterId} se nenajde jako vlastní pole, tak to zkusí
 * jako {@code monster.id} (pole {@code monster}, na něm pole {@code id}) -
 * a vygeneruje JOIN. Je to stejný mechanismus jako u
 * {@code findByMonsterKey} v {@link MonsterRepository}, jen o úroveň
 * složitější (tam je {@code monsterKey} přímo pole na entitě).
 *
 * <p><b>Řazení podle obtížnosti se schválně NEDĚLÁ v SQL.</b> Sloupec
 * {@code difficulty} je {@code TEXT} ('hell'/'nightmare'/'normal') -
 * {@code ORDER BY difficulty} by dalo ABECEDNÍ pořadí (hell, nightmare,
 * normal), ne HERNÍ (normal, nightmare, hell). Šlo by to vyřešit v SQL
 * ({@code CASE WHEN difficulty = 'normal' THEN 0 ...}), ale to by bylo
 * herní pravidlo napsané podruhé, jinde, v jiném jazyce - a časem by se to
 * mohlo rozejít. Herní pořadí už jednou existuje: je to POŘADÍ DEKLARACE
 * konstant v enumu {@link Difficulty} (krok 2, {@code NORMAL, NIGHTMARE,
 * HELL}). Volající (servisní vrstva v kroku 5/6) seřadí tenhle krátký
 * seznam (nejvýš 3 prvky) v Javě podle {@code Difficulty.ordinal()} - jedna
 * implementace pravidla, ne dvě.
 */
public interface MonsterStatRepository extends JpaRepository<MonsterStat, Long> {

    List<MonsterStat> findByMonsterId(Long monsterId);
}
