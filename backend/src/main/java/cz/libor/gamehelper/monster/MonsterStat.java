package cz.libor.gamehelper.monster;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Staty jednoho monstra pro jednu obtížnost. Bez rodiče nemá tenhle řádek
 * smysl - proto (na rozdíl od {@link Monster#gameId}) TADY relaci máme:
 * {@link #monster} je {@code @ManyToOne}, protože {@code MonsterStat} je
 * uvnitř stejného agregátu jako {@code Monster} (v DDD terminologii - viz
 * javadoc {@link #monster}).
 */
@Entity
@Table(
        name = "monster_stat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"monster_id", "difficulty"})
)
public class MonsterStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * {@code @ManyToOne} místo plain {@code Long monsterId}. Pravidlo, které
     * v projektu držíme (viz {@link Monster#gameId} pro opačný případ):
     * vztah v rámci JEDNOHO agregátu (monstrum + jeho staty patří vždycky
     * dohromady, MonsterStat samo o sobě nemá smysl) je objektový vztah.
     * Vztah NAPŘÍČ agregáty (Skill → Monster, viz
     * {@code Skill#monsterKey}) zůstává plain sloupcem a natahuje se
     * explicitním dotazem přes repozitář.
     *
     * <p>{@code FetchType.LAZY}: přístup na {@code getMonster()} vyvolá
     * dodatečný SELECT, jen když se na něj opravdu sáhne. Výchozí hodnota
     * pro {@code @ManyToOne} je v JPA {@code EAGER} - což by znamenalo, že
     * načtení JAKÉHOKOLI {@code MonsterStat} vždycky dotáhne i celé
     * {@code Monster}, i když ho volající nepotřebuje. LAZY tady nastavujeme
     * vždy explicitně, ne proto, že je to nutné zrovna teď, ale aby to
     * nebylo něco, co se musí pamatovat pokaždé zvlášť.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id", nullable = false)
    private Monster monster;

    /**
     * Jak se {@link Difficulty} <-> text 'normal'/'nightmare'/'hell'
     * převádí, viz {@link DifficultyConverter}.
     */
    @Convert(converter = DifficultyConverter.class)
    @Column(name = "difficulty", nullable = false, length = 10)
    private Difficulty difficulty;

    /** Úroveň monstra na dané obtížnosti. Legitimně NULL - viz V2 migrace. */
    @Column(name = "level")
    private Short level;

    @Column(name = "min_hp")
    private Integer minHp;

    @Column(name = "max_hp")
    private Integer maxHp;

    @Column(name = "ac")
    private Integer ac;

    @Column(name = "a1_min_d")
    private Integer a1MinD;

    @Column(name = "a1_max_d")
    private Integer a1MaxD;

    @Column(name = "a1_th")
    private Integer a1Th;

    @Column(name = "a2_min_d")
    private Integer a2MinD;

    @Column(name = "a2_max_d")
    private Integer a2MaxD;

    @Column(name = "a2_th")
    private Integer a2Th;

    @Column(name = "s1_min_d")
    private Integer s1MinD;

    @Column(name = "s1_max_d")
    private Integer s1MaxD;

    @Column(name = "s1_th")
    private Integer s1Th;

    protected MonsterStat() {
        // Viz Monster#Monster() - stejný důvod (JPA specifikace, Hibernate reflexe).
    }

    public Long getId() {
        return id;
    }

    public Monster getMonster() {
        return monster;
    }

    public void setMonster(Monster monster) {
        this.monster = monster;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public Integer getMinHp() {
        return minHp;
    }

    public void setMinHp(Integer minHp) {
        this.minHp = minHp;
    }

    public Integer getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(Integer maxHp) {
        this.maxHp = maxHp;
    }

    public Integer getAc() {
        return ac;
    }

    public void setAc(Integer ac) {
        this.ac = ac;
    }

    public Integer getA1MinD() {
        return a1MinD;
    }

    public void setA1MinD(Integer a1MinD) {
        this.a1MinD = a1MinD;
    }

    public Integer getA1MaxD() {
        return a1MaxD;
    }

    public void setA1MaxD(Integer a1MaxD) {
        this.a1MaxD = a1MaxD;
    }

    public Integer getA1Th() {
        return a1Th;
    }

    public void setA1Th(Integer a1Th) {
        this.a1Th = a1Th;
    }

    public Integer getA2MinD() {
        return a2MinD;
    }

    public void setA2MinD(Integer a2MinD) {
        this.a2MinD = a2MinD;
    }

    public Integer getA2MaxD() {
        return a2MaxD;
    }

    public void setA2MaxD(Integer a2MaxD) {
        this.a2MaxD = a2MaxD;
    }

    public Integer getA2Th() {
        return a2Th;
    }

    public void setA2Th(Integer a2Th) {
        this.a2Th = a2Th;
    }

    public Integer getS1MinD() {
        return s1MinD;
    }

    public void setS1MinD(Integer s1MinD) {
        this.s1MinD = s1MinD;
    }

    public Integer getS1MaxD() {
        return s1MaxD;
    }

    public void setS1MaxD(Integer s1MaxD) {
        this.s1MaxD = s1MaxD;
    }

    public Integer getS1Th() {
        return s1Th;
    }

    public void setS1Th(Integer s1Th) {
        this.s1Th = s1Th;
    }
}
