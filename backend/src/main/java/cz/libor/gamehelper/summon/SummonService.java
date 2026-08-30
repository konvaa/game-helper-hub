package cz.libor.gamehelper.summon;

import cz.libor.gamehelper.common.NotFoundException;
import cz.libor.gamehelper.monster.Difficulty;
import cz.libor.gamehelper.monster.Monster;
import cz.libor.gamehelper.monster.MonsterRepository;
import cz.libor.gamehelper.monster.MonsterStat;
import cz.libor.gamehelper.monster.MonsterStatRepository;
import cz.libor.gamehelper.skill.Skill;
import cz.libor.gamehelper.skill.SkillRepository;
import cz.libor.gamehelper.summon.dto.ComputeRequest;
import cz.libor.gamehelper.summon.dto.ComputeResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastruktura kolem {@link RaiseSkeletonCalculator} - PLAN_P1_SPRING_SLICE.md
 * sekce 7.1: "servisní vrstva nad ním načte data z DB a předá je dovnitř".
 * Tahle třída je JEDINÉ místo v projektu, které entity {@code Skill} a
 * {@code MonsterStat} převádí na {@link RaiseSkeletonInput} - kalkulátor sám
 * o databázi neví nic (viz jeho javadoc).
 *
 * <p>Package-private, stejně jako {@code SkillService}/{@code MonsterService}
 * z kroku 5 - použije ji jen {@link SummonController} ve stejném balíčku,
 * není důvod dělat z ní veřejné API.
 */
@Service
@Transactional(readOnly = true)
class SummonService {

    /**
     * {@code skill_key} skillu Raise Skeleton PO {@code NormalizeKey.normalize("Raise Skeleton")}
     * - "raise_skeleton". Napevno, protože celý tenhle endpoint JE Raise
     * Skeleton (PLAN_P1_SPRING_SLICE.md: "jeden skill variant" pro P1) -
     * kdyby přibyl další summon endpoint, tohle by se stalo parametrem.
     */
    private static final String RAISE_SKELETON_SKILL_KEY = "raise_skeleton";

    private final SkillRepository skillRepository;
    private final MonsterRepository monsterRepository;
    private final MonsterStatRepository monsterStatRepository;

    SummonService(SkillRepository skillRepository,
                  MonsterRepository monsterRepository,
                  MonsterStatRepository monsterStatRepository) {
        this.skillRepository = skillRepository;
        this.monsterRepository = monsterRepository;
        this.monsterStatRepository = monsterStatRepository;
    }

    /**
     * Sestaví {@link RaiseSkeletonInput} z databáze, zavolá čistou funkci
     * {@link RaiseSkeletonCalculator#compute} a výsledek zabalí do
     * {@link ComputeResponse}.
     *
     * <p>Tři kroky, tři různé databázové dotazy - žádný z nich schválně
     * nekombinuje do jednoho JOIN dotazu (na rozdíl od {@code SkillRepository.search}
     * v kroku 4): {@code Skill} a {@code Monster}/{@code MonsterStat} jsou
     * dva different agregáty (viz {@code Skill.monsterKey} javadoc, krok 5),
     * mezi agregáty se v DDD nechodí JOINem, ale přes ID/klíč a repozitář
     * cílového agregátu. Všechny tři běží ve STEJNÉ transakci
     * ({@code @Transactional(readOnly = true)} na třídě), takže to pořád
     * je jeden konzistentní pohled na data, jen ne jeden SQL dotaz.
     *
     * @throws NotFoundException skill "raise_skeleton" v DB neexistuje
     *                            (konfigurační chyba, ne chyba klienta),
     *                            monstrum podle {@code skill.monsterKey}
     *                            neexistuje (totéž), nebo monstrum nemá
     *                            řádek pro požadovanou obtížnost.
     */
    ComputeResponse computeRaiseSkeleton(ComputeRequest request) {
        Skill skill = skillRepository.findBySkillKey(RAISE_SKELETON_SKILL_KEY)
                .orElseThrow(() -> new NotFoundException(
                        "Skill '" + RAISE_SKELETON_SKILL_KEY + "' nenalezen - chybí v datové sadě?"));

        Monster monster = monsterRepository.findByMonsterKey(skill.getMonsterKey())
                .orElseThrow(() -> new NotFoundException(
                        "Monster '" + skill.getMonsterKey() + "' (summon skillu '" + RAISE_SKELETON_SKILL_KEY
                                + "') nenalezen - chybí v datové sadě?"));

        Difficulty difficulty = Difficulty.valueOf(request.difficulty().toUpperCase(Locale.ROOT));

        MonsterStat stat = findStatForDifficulty(monster, difficulty);

        RaiseSkeletonInput input = new RaiseSkeletonInput(
                request.raiseSkeletonLevel(),
                request.skeletonMasteryLevel(),
                new RaiseSkeletonInput.SkillScaling(
                        skill.getEMin(), skill.getEMinLev1(), skill.getEMinLev2(),
                        skill.getEMinLev3(), skill.getEMinLev4(), skill.getEMinLev5()),
                new RaiseSkeletonInput.MonsterCombatStats(
                        stat.getMaxHp(), stat.getA1MinD(), stat.getA1MaxD(), stat.getAc(), stat.getA1Th()));

        RaiseSkeletonResult result = RaiseSkeletonCalculator.compute(input);

        return ComputeResponse.from(request, monster.getId(), result);
    }

    /**
     * {@link MonsterStatRepository#findByMonsterId} vrací VŠECHNY obtížnosti
     * najednou (krok 4 - viz jeho javadoc, proč se v SQL neřadí/nefiltruje
     * podle obtížnosti) - filtr na jednu konkrétní obtížnost je proto tady,
     * ne v repozitáři. Pro necroskeleton jsou to jen 3 řádky, filtrovat v
     * Javě po načtení je levnější než psát další repository metodu jen pro
     * tenhle jeden účel.
     */
    private MonsterStat findStatForDifficulty(Monster monster, Difficulty difficulty) {
        List<MonsterStat> stats = monsterStatRepository.findByMonsterId(monster.getId());
        return stats.stream()
                .filter(stat -> stat.getDifficulty() == difficulty)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Monster " + monster.getId() + " nemá staty pro obtížnost '"
                                + difficulty.name().toLowerCase(Locale.ROOT) + "'"));
    }
}
