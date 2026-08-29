package cz.libor.gamehelper.skill;

import jakarta.persistence.Column;
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
 * Jeden {@code ParamN} skillu (Param1..Param12 z {@code skills_raw},
 * rozložené na řádky místo 12 sloupců navíc na {@link Skill}).
 *
 * <p>Vztah na {@link Skill} JE {@code @ManyToOne} - na rozdíl od
 * {@code Skill#monsterKey} (viz jeho javadoc). Důvod je stejné pravidlo
 * jako u {@code MonsterStat} → {@code Monster}: {@code SkillParam} je uvnitř
 * stejného agregátu jako {@code Skill} a bez něj nedává smysl, zatímco
 * {@code Skill} → {@code Monster} je vztah NAPŘÍČ agregáty.
 */
@Entity
@Table(
        name = "skill_param",
        uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "idx"})
)
public class SkillParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /** Pořadí 1..12, odpovídá ParamN ve zdroji. */
    @Column(name = "idx", nullable = false)
    private Short idx;

    /**
     * V celém datasetu jsou hodnoty vždy celá čísla (ověřeno skenem
     * skills_raw.json, 429 skillů x 12 parametrů). {@code Integer}, ne
     * {@code Double} - vědomé rozhodnutí, viz komentář u sloupce v
     * V2__skills_and_monsters.sql: pokud budoucí refresh datasetu přinese
     * desetinnou hodnotu, chceme, aby import spadl nahlas na chybě typu,
     * ne aby ji tiše zaokrouhlil.
     */
    @Column(name = "value")
    private Integer value;

    /**
     * Obsah "*ParamN Description" (např. "HP % per level"). Není to herní
     * data, ale je to jediná dokumentace k tomu, co který ParamN znamená.
     */
    @Column(name = "description")
    private String description;

    protected SkillParam() {
        // Viz Monster#Monster() - JPA specifikace, Hibernate reflexe.
    }

    public Long getId() {
        return id;
    }

    public Skill getSkill() {
        return skill;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public Short getIdx() {
        return idx;
    }

    public void setIdx(Short idx) {
        this.idx = idx;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
