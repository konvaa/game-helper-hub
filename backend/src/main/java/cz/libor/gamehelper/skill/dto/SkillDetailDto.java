package cz.libor.gamehelper.skill.dto;

import cz.libor.gamehelper.skill.Skill;

/**
 * Tvar pro {@code GET /api/skills/{key}}. Proč vůbec existuje samostatně
 * vedle {@link SkillSummaryDto} (a proč je to DTO, ne entita) - viz
 * {@link SkillSummaryDto} pro celé zdůvodnění, tenhle soubor jen ukazuje
 * DRUHOU stranu stejného principu: "tvar podle endpointu" - detail
 * potřebuje jiná pole než seznam, ne PODMNOŽINU stejných.
 *
 * <p><b>{@link ScalingDto} - proč vnořený record, a ne deset plochých polí
 * {@code eMinLev1..5}/{@code eMaxLev1..5}:</b> těch deset sloupců v entitě
 * (plus {@code emin}/{@code emax}) existuje jen proto, že SQL tabulka
 * neumí pole/strukturu - v Javě tenhle důvod neplatí. {@code minScaling}
 * a {@code maxScaling} spolu tvoří přesně vstup pro funkci {@code seg5()}
 * (PLAN_P1_SPRING_SLICE.md sekce 1.3 - {@code EMin} + pět přírůstků po
 * segmentech efektivní úrovně), takže je seskupí i DTO - kdo čte JSON
 * odpověď, vidí strukturu výpočtu, ne dvacet nesouvisejících čísel za
 * sebou. {@code scalingType} ({@code eType}) zůstává mimo - je jeden
 * společný pro min i max, ne dvojice.
 */
public record SkillDetailDto(
        String key,
        String name,
        String charClass,
        Short reqLevel,
        Short maxLevel,
        String monsterKey,
        String petType,
        String petMaxExpr,
        String scalingType,
        ScalingDto minScaling,
        ScalingDto maxScaling) {

    /**
     * {@code base} + pět přírůstků po segmentech efektivní úrovně (2-8,
     * 9-16, 17-22, 23-28, 29+ - viz {@code seg5()} v plánu). Použije se
     * jednou pro {@code EMin}/{@code EMinLev1..5} ({@link #minScaling}) a
     * jednou pro {@code EMax}/{@code EMaxLev1..5} ({@link #maxScaling}).
     */
    public record ScalingDto(Integer base, Integer lev1, Integer lev2, Integer lev3, Integer lev4, Integer lev5) {
    }

    public static SkillDetailDto from(Skill s) {
        return new SkillDetailDto(
                s.getSkillKey(),
                s.getName(),
                s.getCharClass(),
                s.getReqLevel(),
                s.getMaxLevel(),
                s.getMonsterKey(),
                s.getPetType(),
                s.getPetMaxExpr(),
                s.getEType(),
                new ScalingDto(s.getEMin(), s.getEMinLev1(), s.getEMinLev2(), s.getEMinLev3(), s.getEMinLev4(), s.getEMinLev5()),
                new ScalingDto(s.getEMax(), s.getEMaxLev1(), s.getEMaxLev2(), s.getEMaxLev3(), s.getEMaxLev4(), s.getEMaxLev5()));
    }
}
