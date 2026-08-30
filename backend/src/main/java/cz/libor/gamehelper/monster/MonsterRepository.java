package cz.libor.gamehelper.monster;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Přístup k datům monster. {@code GET /api/monsters/{id}} (krok 5) čte
 * podle surogátního {@code id} - to už umí zděděná metoda
 * {@code JpaRepository#findById(Object)}, žádnou vlastní metodu pro to
 * nepotřebuje, proto tady žádná není.
 *
 * <p>{@link #findByMonsterKey(String)} je tu pro budoucí použití (krok 6 -
 * {@code RaiseSkeletonCalculator} bude potřebovat dotáhnout monstrum podle
 * {@code Skill#getMonsterKey()}, ne podle číselného ID), ne pro krok 4/5
 * samotný. Přidávám ji teď spolu s ostatními repozitáři, ať je tahle vrstva
 * hotová najednou a krok 6 si jen sáhne pro hotové.
 */
public interface MonsterRepository extends JpaRepository<Monster, Long> {

    Optional<Monster> findByMonsterKey(String monsterKey);
}
