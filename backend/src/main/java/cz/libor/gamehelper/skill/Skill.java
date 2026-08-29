package cz.libor.gamehelper.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Projekce {@code skills_raw} (322 → 26 sloupců) - viz
 * {@code docs/SKILLS_PROJECTION.md} sekce 3 pro zdůvodnění KAŽDÉHO
 * zahrnutého i vynechaného sloupce, a {@code docs/PLAN_P1_SPRING_SLICE.md}
 * sekce 4.4 pro rozhodnutí o typech.
 *
 * <p><b>{@code monsterKey} je plain {@code String}, ne {@code @ManyToOne
 * Monster}</b> - na rozdíl od {@link cz.libor.gamehelper.monster.MonsterStat#getMonster()}.
 * Skill a Monster jsou dva different agregáty (jeden skill nepatří
 * monstru a naopak); mezi agregáty se v DDD nechodí objektovým grafem, ale
 * přes ID a repozitář cílového agregátu - servisní vrstva v kroku 6 si
 * monstrum dotáhne přes {@code MonsterRepository.findByMonsterKey(...)}
 * sama, až ho bude potřebovat. Databázová referenční integrita (FK
 * {@code references monster(monster_key)}) přitom platí bez ohledu na to,
 * jestli ji Java modeluje jako objekt - FK v SQL a {@code @ManyToOne} v Javě
 * jsou dvě nezávislé věci, jedna chrání data, druhá je pohodlí pro kód.
 */
@Entity
@Table(name = "skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Viz {@link cz.libor.gamehelper.monster.Monster#getGameId()} - stejný důvod, plain sloupec. */
    @Column(name = "game_id", nullable = false)
    private Long gameId;

    /**
     * Business klíč, používá se v URL ({@code GET /api/skills/{key}}).
     * MUSÍ být bit-shodný s výstupem Python {@code normalize_key()} - jinak
     * by baseline testy v kroku 6 srovnávaly výsledky pro different skilly.
     * Normalizace probíhá v importeru (krok 3), ne tady - entita jen ukládá
     * hotovou hodnotu.
     */
    @Column(name = "skill_key", nullable = false, unique = true)
    private String skillKey;

    /**
     * {@code *Id} z exportu. NENÍ autoritativní - je to komentářový sloupec,
     * který hra nečte (SKILLS_PROJECTION.md 3.1.A). Autoritativní je
     * {@link #skillKey}.
     */
    @Column(name = "ext_id")
    private Integer extId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "charclass")
    private String charClass;

    @Column(name = "skilldesc_key")
    private String skilldescKey;

    @Column(name = "reqlevel")
    private Short reqLevel;

    /**
     * Strop INVESTOVANÝCH BODŮ, ne efektivní úrovně. Compute endpoint
     * (krok 6) tímhle polem NIKDY neomezuje vstup - viz
     * SKILLS_PROJECTION.md 2.5.
     */
    @Column(name = "maxlvl")
    private Short maxLevel;

    // --- segmentové škálování (seg5), SKILLS_PROJECTION.md sekce 2 ---

    @Column(name = "etype")
    private String eType;

    @Column(name = "emin")
    private Integer eMin;

    @Column(name = "emax")
    private Integer eMax;

    @Column(name = "emin_lev1")
    private Integer eMinLev1;

    @Column(name = "emin_lev2")
    private Integer eMinLev2;

    @Column(name = "emin_lev3")
    private Integer eMinLev3;

    @Column(name = "emin_lev4")
    private Integer eMinLev4;

    /**
     * Nejdůležitější sloupec celé projekce (spolu s {@link #eMaxLev5}):
     * jediný, co roste nad efektivní úrovní 28, a bez horní meze.
     * SKILLS_PROJECTION.md 2.2.
     */
    @Column(name = "emin_lev5")
    private Integer eMinLev5;

    @Column(name = "emax_lev1")
    private Integer eMaxLev1;

    @Column(name = "emax_lev2")
    private Integer eMaxLev2;

    @Column(name = "emax_lev3")
    private Integer eMaxLev3;

    @Column(name = "emax_lev4")
    private Integer eMaxLev4;

    @Column(name = "emax_lev5")
    private Integer eMaxLev5;

    // --- summon ---

    @Column(name = "monster_key")
    private String monsterKey;

    @Column(name = "pettype")
    private String petType;

    /**
     * VÝRAZ ("(lvl &lt; 4) ?lvl:(2+lvl/3)"), ne číslo - proto {@code String}.
     * Ve slice se přes vyhodnocovač nepoužívá (RaiseSkeletonCalculator má
     * počet skeletonů zakódovaný natvrdo, ověřeno proti baseline -
     * PLAN_P1_SPRING_SLICE.md 1.3); sloupec je tu pro úplnost projekce a pro
     * detail endpoint, který ho může vrátit jako surový text.
     */
    @Column(name = "petmax_expr")
    private String petMaxExpr;

    @Column(name = "hit_shift")
    private Short hitShift;

    // --- výrazové sloupce, stejný důvod jako petMaxExpr ---

    @Column(name = "calc1_expr")
    private String calc1Expr;

    @Column(name = "aurastat1")
    private String auraStat1;

    @Column(name = "aurastat1_calc_expr")
    private String auraStat1CalcExpr;

    @Column(name = "src_dam")
    private Short srcDam;

    protected Skill() {
        // Viz Monster#Monster() - JPA specifikace vyžaduje bezparametrický
        // konstruktor, Hibernate pole plní reflexí až po vytvoření instance.
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public String getSkillKey() {
        return skillKey;
    }

    public void setSkillKey(String skillKey) {
        this.skillKey = skillKey;
    }

    public Integer getExtId() {
        return extId;
    }

    public void setExtId(Integer extId) {
        this.extId = extId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCharClass() {
        return charClass;
    }

    public void setCharClass(String charClass) {
        this.charClass = charClass;
    }

    public String getSkilldescKey() {
        return skilldescKey;
    }

    public void setSkilldescKey(String skilldescKey) {
        this.skilldescKey = skilldescKey;
    }

    public Short getReqLevel() {
        return reqLevel;
    }

    public void setReqLevel(Short reqLevel) {
        this.reqLevel = reqLevel;
    }

    public Short getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(Short maxLevel) {
        this.maxLevel = maxLevel;
    }

    public String getEType() {
        return eType;
    }

    public void setEType(String eType) {
        this.eType = eType;
    }

    public Integer getEMin() {
        return eMin;
    }

    public void setEMin(Integer eMin) {
        this.eMin = eMin;
    }

    public Integer getEMax() {
        return eMax;
    }

    public void setEMax(Integer eMax) {
        this.eMax = eMax;
    }

    public Integer getEMinLev1() {
        return eMinLev1;
    }

    public void setEMinLev1(Integer eMinLev1) {
        this.eMinLev1 = eMinLev1;
    }

    public Integer getEMinLev2() {
        return eMinLev2;
    }

    public void setEMinLev2(Integer eMinLev2) {
        this.eMinLev2 = eMinLev2;
    }

    public Integer getEMinLev3() {
        return eMinLev3;
    }

    public void setEMinLev3(Integer eMinLev3) {
        this.eMinLev3 = eMinLev3;
    }

    public Integer getEMinLev4() {
        return eMinLev4;
    }

    public void setEMinLev4(Integer eMinLev4) {
        this.eMinLev4 = eMinLev4;
    }

    public Integer getEMinLev5() {
        return eMinLev5;
    }

    public void setEMinLev5(Integer eMinLev5) {
        this.eMinLev5 = eMinLev5;
    }

    public Integer getEMaxLev1() {
        return eMaxLev1;
    }

    public void setEMaxLev1(Integer eMaxLev1) {
        this.eMaxLev1 = eMaxLev1;
    }

    public Integer getEMaxLev2() {
        return eMaxLev2;
    }

    public void setEMaxLev2(Integer eMaxLev2) {
        this.eMaxLev2 = eMaxLev2;
    }

    public Integer getEMaxLev3() {
        return eMaxLev3;
    }

    public void setEMaxLev3(Integer eMaxLev3) {
        this.eMaxLev3 = eMaxLev3;
    }

    public Integer getEMaxLev4() {
        return eMaxLev4;
    }

    public void setEMaxLev4(Integer eMaxLev4) {
        this.eMaxLev4 = eMaxLev4;
    }

    public Integer getEMaxLev5() {
        return eMaxLev5;
    }

    public void setEMaxLev5(Integer eMaxLev5) {
        this.eMaxLev5 = eMaxLev5;
    }

    public String getMonsterKey() {
        return monsterKey;
    }

    public void setMonsterKey(String monsterKey) {
        this.monsterKey = monsterKey;
    }

    public String getPetType() {
        return petType;
    }

    public void setPetType(String petType) {
        this.petType = petType;
    }

    public String getPetMaxExpr() {
        return petMaxExpr;
    }

    public void setPetMaxExpr(String petMaxExpr) {
        this.petMaxExpr = petMaxExpr;
    }

    public Short getHitShift() {
        return hitShift;
    }

    public void setHitShift(Short hitShift) {
        this.hitShift = hitShift;
    }

    public String getCalc1Expr() {
        return calc1Expr;
    }

    public void setCalc1Expr(String calc1Expr) {
        this.calc1Expr = calc1Expr;
    }

    public String getAuraStat1() {
        return auraStat1;
    }

    public void setAuraStat1(String auraStat1) {
        this.auraStat1 = auraStat1;
    }

    public String getAuraStat1CalcExpr() {
        return auraStat1CalcExpr;
    }

    public void setAuraStat1CalcExpr(String auraStat1CalcExpr) {
        this.auraStat1CalcExpr = auraStat1CalcExpr;
    }

    public Short getSrcDam() {
        return srcDam;
    }

    public void setSrcDam(Short srcDam) {
        this.srcDam = srcDam;
    }
}
