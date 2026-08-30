package cz.libor.gamehelper.monster;

import cz.libor.gamehelper.common.NotFoundException;
import cz.libor.gamehelper.monster.dto.MonsterDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servisní vrstva pro {@link MonsterController}. Package-private ze
 * stejného důvodu jako {@code SkillService} - viz jeho javadoc.
 *
 * <p>Volá DVA repozitáře v JEDNÉ transakci ({@code @Transactional} na
 * třídě) - {@link MonsterRepository#findById} pro identitu monstra a
 * {@link MonsterStatRepository#findByMonsterId} pro jeho staty. Oddělené
 * dotazy jsou tu záměrně (krok 2: {@code Monster} nemá {@code
 * @OneToMany<MonsterStat>} kolekci), ale musí proběhnout ve STEJNÉ
 * transakci/session, jinak by druhý dotaz mohl vidět jiný stav databáze
 * než první (u nás se data neminí, protože dataset je po importu
 * neměnný, ale je to obecně správný důvod, proč obě volání zabalit do
 * jedné metody s {@code @Transactional}, ne dvě samostatné servisní
 * metody volané controllerem zvlášť).
 */
@Service
@Transactional(readOnly = true)
class MonsterService {

    private final MonsterRepository monsterRepository;
    private final MonsterStatRepository monsterStatRepository;

    MonsterService(MonsterRepository monsterRepository, MonsterStatRepository monsterStatRepository) {
        this.monsterRepository = monsterRepository;
        this.monsterStatRepository = monsterStatRepository;
    }

    /** {@code GET /api/monsters/{id}}. Stejný vzor jako {@code SkillService.getByKey}. */
    MonsterDto getById(Long id) {
        Monster monster = monsterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Monster " + id + " not found"));
        List<MonsterStat> stats = monsterStatRepository.findByMonsterId(id);
        return MonsterDto.from(monster, stats);
    }
}
